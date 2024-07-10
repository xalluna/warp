package alluna.warp.Locations;

import net.minecraft.server.command.ServerCommandSource;

import java.time.OffsetDateTime;

public abstract class Location {
	public String label;
	public String dimension;
	public double x;
	public double y;
	public double z;
	public float pitch;
	public float yaw;

//	public OffsetDateTime createdDate;

	public Location(ServerCommandSource source, String label) {
		var position = source.getPosition();
		var player = source.getPlayer();
		var dimension = source.getWorld().getRegistryKey().getValue().toString();

		this.label = label;
		this.dimension = dimension;
		this.x = position.x;
		this.y = position.y;
		this.z = position.z;
		this.pitch = player.getPitch();
		this.yaw = player.getYaw();

//		this.createdDate = OffsetDateTime.now();
	}

	public Location() {

	}

	public String getLabel() {
		return this.label;
	}
}
