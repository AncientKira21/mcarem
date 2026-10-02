package com.ancientkira.mca.entity.ai.relationship;

import com.ancientkira.mca.entity.EntityWrapper;

public interface CompassionateEntity<T extends EntityRelationship> extends EntityWrapper {
    T getRelationships();
}
