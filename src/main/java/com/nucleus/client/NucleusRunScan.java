package com.nucleus.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nucleus.NucleusMod;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class NucleusRunScan {
    private static long nextScan;
    private static boolean pending;
    private static final List<String> CRYSTALS = List.of("amber", "amethyst", "jade", "sapphire", "topaz");
    record Result(String profile, long runs, boolean fromCrystals) {}

    record Profile(String name, JsonObject member) {}

    static int scan(FabricClientCommandSource source) {
        var store = NucleusMod.SPEEDRUN;
        long completed = store.completedRuns, manual = store.manualRunTotal;
        Function<Result,String> apply = result -> {
            if (store.completedRuns != completed || store.manualRunTotal != manual)
                return "Your run count changed during the scan. Scan again to apply the profile total.";
            store.completedRuns = result.runs();
            store.manualRunTotal = -1;
            store.save();
            Achievements.recheck(false);
            return result.profile() + " — " + SpeedrunManager.counter()
                + (result.fromCrystals() ? " (from crystal placements)" : "") + (manual >= 0 ? ". Manual total replaced." : ".");
        };
        return lookup(source::sendFeedback, NucleusRunScan::runTotal, apply,
            name -> fishRuns(Minecraft.getInstance(), name));
    }

    private static CompletableFuture<Result> fishRuns(Minecraft client, String name) {
        var result = new CompletableFuture<Result>();
        try {
            var api = Class.forName("fishmod.utils.HypixelApi");
            var callback = Class.forName("fishmod.utils.HypixelApi$IntCallback");
            var listener = fishCallback(callback,result);
            api.getMethod("getNucleusRuns", Minecraft.class, String.class, callback).invoke(null,client,name,listener);
        } catch (ReflectiveOperationException | LinkageError e) {
            NucleusMod.LOGGER.debug("FishMod Nucleus lookup unavailable",e);
            result.completeExceptionally(new ScanError("This FishMod version could not start a Nucleus lookup."));
        }
        return result;
    }

    static Object fishCallback(Class<?> callback, CompletableFuture<Result> result) {
        return Proxy.newProxyInstance(callback.getClassLoader(),new Class<?>[]{callback},(proxy,method,args)->{
            if (method.getDeclaringClass()==Object.class) return switch(method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy==args[0];
                default -> "KCA Nucleus run lookup";
            };
            int runs = (int)args[0];
            if (runs < 0) result.completeExceptionally(new ScanError("FishMod could not read a run total for your active profile."));
            else result.complete(new Result("Active profile (FishMod)",runs,false));
            return null;
        });
    }

    static <T> int lookup(java.util.function.Consumer<Component> output, java.util.function.Function<Profile,T> read,
                          java.util.function.Function<T,String> apply) {
        return lookup(output, read, apply, null);
    }

    record Provider<T>(String name, Supplier<CompletableFuture<T>> request) {}

    static <T> int lookup(Consumer<Component> output, Function<Profile,T> read, Function<T,String> apply,
                          Function<String,CompletableFuture<T>> fish) {
        var client = Minecraft.getInstance();
        var player = client.player;
        if (player == null) return feedback(output,"Join a world before scanning your SkyBlock profile.");
        String uuid = player.getUUID().toString().replace("-", "");
        String name = player.getGameProfile().name();
        String key = System.getenv("KCA_HYPIXEL_API_KEY");
        var providers = new java.util.ArrayList<Provider<T>>();
        if (key != null && !key.isBlank()) providers.add(new Provider<>("Hypixel", () ->
            withKey(uuid,key).thenApply(json -> read.apply(selectedProfile(json,uuid)))));
        if (fish != null && FabricLoader.getInstance().isModLoaded("fishmod-dungeons"))
            providers.add(new Provider<>("FishMod", () -> fish.apply(name)));
        if (FabricLoader.getInstance().isModLoaded("skyblocker")) providers.add(new Provider<>("Skyblocker", () ->
            withSkyblocker(uuid).thenApply(json -> read.apply(selectedProfile(json,uuid)))));
        if (providers.isEmpty()) return feedback(output, "Scanning needs a Hypixel API key, "
            + (fish == null ? "" : "FishMod, ") + "or Skyblocker.");
        var level = client.level;
        return perform(output,"Checking your active profile through "+providers.getFirst().name()+"…",
            () -> tryProviders(providers, notice -> client.execute(() -> {
                if(client.level==level && client.player==player)feedback(output,notice);
            })), apply, 16*providers.size()+1);
    }

    static <T> CompletableFuture<T> tryProviders(List<Provider<T>> providers,Consumer<String> notice) {
        CompletableFuture<T> attempt = request(providers.getFirst());
        for(int i=1;i<providers.size();i++){
            var previous=providers.get(i-1);
            var next=providers.get(i);
            attempt=attempt.exceptionallyComposeAsync(error -> {
                notice.accept(previous.name()+" lookup failed. "+detail(error)+" Trying "+next.name()+"…");
                return request(next);
            });
        }
        return attempt;
    }

    private static <T> CompletableFuture<T> request(Provider<T> provider) {
        try { return provider.request().get().orTimeout(15,TimeUnit.SECONDS); }
        catch(RuntimeException | LinkageError error) { return CompletableFuture.failedFuture(error); }
    }

    private static <T> int perform(Consumer<Component> output,String message,Supplier<CompletableFuture<T>> fetch,
                                   Function<T,String> apply,int timeoutSeconds) {
        var client = Minecraft.getInstance();
        if (pending) return feedback(output, "A profile scan is already in progress.");
        if (System.nanoTime() < nextScan) return feedback(output, "Please wait a few seconds before scanning again.");
        var level = client.level;
        pending = true;
        nextScan = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        feedback(output,message);
        CompletableFuture.supplyAsync(fetch).thenCompose(Function.identity())
            .orTimeout(timeoutSeconds, TimeUnit.SECONDS).whenCompleteAsync((result,error) -> {
                pending = false;
                if (client.level != level || client.player == null) return;
                if (error == null) {
                    feedback(output,apply.apply(result));
                    return;
                }
                feedback(output,detail(error) + " Your saved count was kept.");
            },client);
        return 1;
    }

    private static String detail(Throwable error) {
        while (error.getCause()!=null) error=error.getCause();
        return error instanceof ScanError ? error.getMessage() : "Profile lookup failed or timed out. Try again later.";
    }

    private static int feedback(java.util.function.Consumer<Component> output, String text) {
        output.accept(Component.literal(text));
        return 1;
    }

    private static CompletableFuture<JsonObject> withKey(String uuid, String key) {
        var request = HttpRequest.newBuilder(URI.create("https://api.hypixel.net/v2/skyblock/profiles?uuid=" + uuid))
            .timeout(Duration.ofSeconds(10)).header("API-Key", key).build();
        return Http.CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() != 200) throw new ScanError("Hypixel returned HTTP " + response.statusCode() + ".");
            return JsonParser.parseString(response.body()).getAsJsonObject();
        });
    }

    private static class Http {
        static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    private static CompletableFuture<JsonObject> withSkyblocker(String uuid) {
        try {
            var provider = Class.forName("de.hysky.skyblocker.utils.ProfileUtils").getMethod("fetchFullProfileByUuid", String.class);
            var future = (CompletableFuture<?>) provider.invoke(null, uuid);
            return future.thenApply(value -> {
                if (value instanceof JsonObject profile) return profile;
                throw new ScanError("Skyblocker could not fetch your Hypixel profile.");
            });
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new ScanError("This Skyblocker version could not provide profile data. You can also set KCA_HYPIXEL_API_KEY.");
        }
    }

    static Profile selectedProfile(JsonObject response, String uuid) {
        if (response == null || !response.has("profiles") || !response.get("profiles").isJsonArray())
            throw new ScanError("The API returned no SkyBlock profiles.");
        for (var entry : response.getAsJsonArray("profiles")) {
            var profile = entry.getAsJsonObject();
            if (!profile.has("selected") || !profile.get("selected").getAsBoolean()) continue;
            var member = object(object(profile, "members"), uuid);
            if (member == null) throw new ScanError("Your member data is unavailable.");
            return new Profile(profile.has("cute_name") ? profile.get("cute_name").getAsString() : "Active profile", member);
        }
        throw new ScanError("The API did not identify an active profile for you.");
    }

    private static Result runTotal(Profile profile) {
        String name = profile.name();
        var core = object(profile.member(), "mining_core");
        var nucleus = object(core, "crystal_nucleus");
        if (nucleus != null && nucleus.has("times_completed"))
            return new Result(name, count(nucleus.get("times_completed")), false);
        var crystals = object(core, "crystals");
        if (crystals == null) throw new ScanError("Your profile did not return Nucleus mining data.");
        long minimum = Long.MAX_VALUE;
        for (var crystal : CRYSTALS) {
            var stats = object(crystals, crystal + "_crystal");
            if (stats == null || !stats.has("total_placed"))
                throw new ScanError("Your profile did not return all five crystal placement counts.");
            minimum = Math.min(minimum, count(stats.get("total_placed")));
        }
        return new Result(name, minimum, true);
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }

    private static long count(JsonElement value) {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            long result = value.getAsBigDecimal().longValueExact();
            if (result >= 0 && result < Long.MAX_VALUE) return result;
        }
        throw new ScanError("The API returned an invalid run count.");
    }

    static final class ScanError extends RuntimeException {
        ScanError(String message) { super(message); }
    }
}
