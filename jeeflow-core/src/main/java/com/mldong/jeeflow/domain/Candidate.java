package com.mldong.jeeflow.domain;

/**
 * 候选人值对象
 *
 * @author mldong
 */
public class Candidate {

    private String actorId;
    private String actorName;
    private String type; // user / dept / role

    public Candidate() {}

    public Candidate(String actorId, String actorName, String type) {
        this.actorId = actorId;
        this.actorName = actorName;
        this.type = type;
    }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Candidate)) return false;
        Candidate c = (Candidate) o;
        return actorId != null && actorId.equals(c.actorId);
    }

    @Override
    public int hashCode() {
        return actorId != null ? actorId.hashCode() : 0;
    }
}
