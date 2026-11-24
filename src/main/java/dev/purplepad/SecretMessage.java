package dev.purplepad;

final class SecretMessage {
    static final int MAX_LENGTH = 120;
    static final String DEFAULT_TEMPLATE="{name} | Secrets: {secrets} | Rooms: {rooms} | Deaths: {deaths}";
    private SecretMessage() {}

    static String format(String template, String name, int solo, int shared, String secrets, int deaths,
                         boolean compact, char secretColor, char roomColor) {
        int total = solo + shared;
        String rooms = solo == total ? Integer.toString(solo) : solo + "-" + total;
        String sc = "§" + secretColor, rc = "§" + roomColor;
        if(template.isBlank() || template.equals(DEFAULT_TEMPLATE)){
            if(compact)return sc + secrets + " Secrets  §8·  " + rc + rooms + " Rooms";
            template=DEFAULT_TEMPLATE;
        }
        return template.replace("{name}", name).replace("{secrets}", sc + secrets + "§r")
            .replace("{rooms}", rc + rooms + "§r").replace("{solo}", Integer.toString(solo))
            .replace("{shared}", Integer.toString(shared)).replace("{deaths}", Integer.toString(deaths));
    }
}
