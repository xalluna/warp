package alluna.warp;

import java.util.ArrayList;

public class Location {
    public String label;
    public String dimension;
    public double x;
    public double y;
    public double z;
    public float pitch;
    public float yaw;

    public String createdBy;
    public boolean isPrivate;

    public ArrayList<String> allowedPlayers = new ArrayList<>();

    public String getLabel() {
        return this.label;
    }

    public boolean isOwnedBy(String name) {
        return this.createdBy.equals(name);
    }

    public boolean isAllowedBy(String name) {
        return this.allowedPlayers.stream().anyMatch(x -> x.equals(name));
    }

    public boolean allow(String name) {
        if (this.isAllowedBy(name)) {
            return false;
        }

        this.allowedPlayers.add(name);
        return true;
    }

    public boolean disallow(String name) {
        if (!this.isAllowedBy(name)) {
            return false;
        }

        this.allowedPlayers.remove(name);
        return true;
    }
}
