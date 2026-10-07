package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One directed reference from a build unit to a type, together with the strength of that reference.
 *
 * <p>{@code apiWidth} counts the distinct members of the target that the build unit touches. It is the size
 * of the interface that survives a separation, which is what a separation actually costs, and it does not
 * grow when the same member is called repeatedly.
 *
 * <p>{@code traffic} counts the reference sites. A repeatedly used but narrow interface stays visible here
 * even though it stays cheap to separate.
 *
 * <p>{@code structural} marks a superclass, an implemented interface or a declared field type. That is a
 * fact about the type system rather than a measured coupling: the diagram draws it regardless of any cutoff,
 * and on its own it carries no reference site, so it adds nothing to a separation cost.
 */
public class ClassReference implements Comparable<ClassReference> {
    private final String target;
    private final int apiWidth;
    private final int traffic;
    private final boolean structural;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ClassReference(@JsonProperty("target") String target,
                          @JsonProperty("apiWidth") int apiWidth,
                          @JsonProperty("traffic") int traffic,
                          @JsonProperty("structural") boolean structural) {
        this.target = target;
        this.apiWidth = apiWidth;
        this.traffic = traffic;
        this.structural = structural;
    }

    public String getTarget() {
        return target;
    }

    public int getApiWidth() {
        return apiWidth;
    }

    public int getTraffic() {
        return traffic;
    }

    @JsonProperty("structural")
    public boolean isStructural() {
        return structural;
    }

    /**
     * @return true when no reference site was measured, so the reference rests on the type system alone
     * and carries no separation cost
     */
    @JsonIgnore
    public boolean isStructuralOnly() {
        return structural && traffic == 0;
    }

    @Override
    public int compareTo(ClassReference other) {
        return target.compareTo(other.target);
    }

    @Override
    public String toString() {
        return target + "(apiWidth=" + apiWidth + ", traffic=" + traffic + ", structural=" + structural + ")";
    }
}
