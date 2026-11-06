package com.ancientkira.mca.server;

/** Registry-ID checks also cover old saves whose family-tree profession was not updated. */
public final class RevivalRaiderFilter {
    private RevivalRaiderFilter() {}
    public static boolean profession(String id) {
        String path=id.substring(id.indexOf(':')+1);
        return path.equals("pillager") || path.equals("vindicator") || path.equals("evoker") || path.equals("witch");
    }
    public static boolean entity(String id) {
        String path=id.substring(id.indexOf(':')+1);
        return profession(id) || path.equals("ravager") || path.equals("raider_golem")
                || path.equals("male_pillager") || path.equals("female_pillager")
                || path.equals("male_vindicator") || path.equals("female_vindicator")
                || path.equals("male_evoker") || path.equals("female_evoker")
                || path.equals("male_witch") || path.equals("female_witch");
    }
}
