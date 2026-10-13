package com.ancientkira.mca.util;

public record McaPair<A, B>(A a, B b) {
    public A getA() {
        return a;
    }

    public B getB() {
        return b;
    }
}
