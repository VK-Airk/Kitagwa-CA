package dev.purplepad;

import com.nucleus.NucleusConfig;
import com.nucleus.NucleusConfig.DamageColor;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.WeakHashMap;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.decoration.ArmorStand;

public final class DamageNumbers {
    private static final Pattern HIT = Pattern.compile(
        "([✧✯]?)([0-9]{1,18}|[0-9]{1,3}(?:,[0-9]{3}){1,5})([⚔+✧❤♞☄✷ﬗ✯]*)");
    private static final String[] UNITS = {"","K","M","B","T","Q","Qi"};
    private static final WeakHashMap<ArmorStand,Label> labels = new WeakHashMap<>();
    private record Label(Component original, int settings, Component display) {}

    private DamageNumbers() {}

    public static Component display(ArmorStand stand,Component name,NucleusConfig config){
        int settings = figures(config) | (color(config).ordinal()<<3)
            | (config.showDamageSymbols?64:0) | (config.lowercaseDamageUnits?128:0)
            | (config.showDamageSplashes?256:0) | (config.compactDamageNumbers?512:0);
        var cached=labels.get(stand);
        if(cached!=null && cached.settings==settings && cached.original.equals(name))return cached.display;
        var display=format(name,config);
        labels.put(stand,new Label(name,settings,display));
        return display;
    }

    static int figures(NucleusConfig config){return Math.clamp(config.damageSignificantFigures,1,4);}
    static DamageColor color(NucleusConfig config){return config.damageColor==null?DamageColor.Original:config.damageColor;}

    static Component format(Component name,NucleusConfig config){
        if(config.showDamageSplashes && !config.compactDamageNumbers)return name;
        var match=HIT.matcher(ChatFormatting.stripFormatting(name.getString()).strip());
        if(!match.matches())return name;
        String before=match.group(1),after=match.group(3);
        // A bare number followed by a heart also labels mob health.
        if(before.isEmpty() && after.equals("❤"))return name;
        if(!config.showDamageSplashes)return null;
        String digits=match.group(2).replace(",","");
        String compact=abbreviate(digits,figures(config),config.lowercaseDamageUnits);
        if(!config.showDamageSymbols){before="";after="";}
        var firstDigitStyle=new Style[]{name.getStyle()};
        name.getVisualOrderText().accept((index,style,code)->{
            if(code<'0'||code>'9')return true;
            firstDigitStyle[0]=style;
            return false;
        });
        var tint=switch(color(config)){
            case Original -> null; case Gold -> ChatFormatting.GOLD; case Aqua -> ChatFormatting.AQUA;
            case Pink -> ChatFormatting.LIGHT_PURPLE; case Red -> ChatFormatting.RED; case White -> ChatFormatting.WHITE;
        };
        var style=tint==null?firstDigitStyle[0]:firstDigitStyle[0].withColor(tint);
        return Component.literal(before+compact+after).setStyle(style);
    }

    private static String abbreviate(String digits,int figures,boolean lowercase){
        BigDecimal rounded=new BigDecimal(digits).round(new MathContext(figures,RoundingMode.HALF_UP));
        int integerDigits=rounded.precision()-rounded.scale();
        // Choose the unit after rounding so 999,500 becomes 1.0M.
        int group=Math.max(0,(integerDigits-1)/3);
        int decimals=Math.max(0,figures-(integerDigits-group*3));
        String number=rounded.movePointLeft(group*3).setScale(decimals).toPlainString();
        String unit=UNITS[group];
        return number+(lowercase?unit.toLowerCase(Locale.ROOT):unit);
    }
}
