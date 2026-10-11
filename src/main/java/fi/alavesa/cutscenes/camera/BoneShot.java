package fi.alavesa.cutscenes.camera;

import fi.alavesa.cutscenes.CutsceneRun;
import fi.alavesa.cutscenes.ModelActor;
import org.joml.Vector3d;

/**
 * The camera rides a bone of an actor - by default the model's camera group (config camera.bone-name).
 * Where it points: an explicit look-at target if given, else the model's camera_target group if it has
 * one, else along the camera group's forward axis (config camera.forward-axis). Falls back to the
 * actor's own position looking the way the actor faces when the bone does not exist.
 *
 * @param bone   bone to ride, null = configured camera bone
 * @param lookActor/lookBone optional look-at target (lookBone null = the actor's position)
 */
public record BoneShot(String actor, String bone, String lookActor, String lookBone) implements CameraShot {

    @Override
    public Pose pose(CutsceneRun run, int localTick) {
        ModelActor a = run.actor(actor);
        if (a == null) return null;
        String boneName = bone != null ? bone : run.plugin().settings().cameraBone;
        Vector3d pos = a.bonePosition(boneName);
        if (pos == null) {
            if (localTick == 0) run.warn("camera: actor '" + actor + "' has no bone '" + boneName + "' (bones: " + a.boneNames() + ")");
            var l = a.location();
            return new Pose(l.getWorld(), l.getX(), l.getY() + 1.6, l.getZ(), l.getYaw(), l.getPitch());
        }
        Vector3d target = null;
        if (lookActor != null) target = run.pointOf(lookActor, lookBone);
        if (target == null) target = a.bonePosition(run.plugin().settings().targetBone);
        if (target != null) return Pose.lookingAt(a.location().getWorld(), pos, target);
        Vector3d fwd = a.boneAxis(boneName, run.plugin().settings().forwardAxis);
        if (fwd == null) {
            var l = a.location();
            return new Pose(l.getWorld(), pos.x, pos.y, pos.z, l.getYaw(), l.getPitch());
        }
        return Pose.lookingAt(a.location().getWorld(), pos, new Vector3d(pos).add(fwd));
    }

    @Override public String describe() {
        return "bone " + actor + "/" + (bone == null ? "<camera>" : bone) + (lookActor == null ? "" : " look-at " + lookActor + (lookBone == null ? "" : "/" + lookBone));
    }
}
