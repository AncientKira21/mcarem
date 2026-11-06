package com.ancientkira.mca.entity;

/** Explicit resurrection resets the committed death state as well as health. */
public interface RevivalState {
    void mca$resetDeathState();
}
