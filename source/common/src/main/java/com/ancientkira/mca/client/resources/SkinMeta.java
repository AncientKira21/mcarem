package com.ancientkira.mca.client.resources;

import com.ancientkira.mca.entity.ai.relationship.Gender;

public class SkinMeta {
    private final String profession;
    private final int temperature;
    private final int gender;
    private final float chance;
    private boolean untinted;

    public SkinMeta(String profession, int temperature, int gender, float chance) {
        this.profession = profession;
        this.temperature = temperature;
        this.gender = gender;
        this.chance = chance;
    }

    public SkinMeta(String profession,int temperature,int gender,float chance,boolean untinted) {
        this(profession,temperature,gender,chance);this.untinted=untinted;
    }
    public boolean isUntinted(){return untinted;}

    public String getProfession() {
        return profession;
    }

    public int getTemperature() {
        return temperature;
    }

    public Gender getGender() {
        return Gender.byId(gender);
    }

    public float getChance() {
        return chance;
    }
}
