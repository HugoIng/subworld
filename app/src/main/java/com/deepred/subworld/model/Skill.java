package com.deepred.subworld.model;

/**
 *
 */
public class Skill {
    public int value;
    public float evolutionFactor;

    public Skill(int val, float factor) {
        value = val;
        evolutionFactor = factor;
    }

    public Skill() {
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public float getEvolutionFactor() {
        return evolutionFactor;
    }

    public void setEvolutionFactor(float evolutionFactor) {
        this.evolutionFactor = evolutionFactor;
    }
}
