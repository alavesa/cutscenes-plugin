package fi.alavesa.cutscenes.camera;

import fi.alavesa.cutscenes.CutsceneRun;

/** Fixed camera. */
public record StaticShot(Pose pose) implements CameraShot {
    @Override public Pose pose(CutsceneRun run, int localTick) { return pose; }
    @Override public String describe() {
        return String.format("static %.1f %.1f %.1f yaw %.0f pitch %.0f", pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch());
    }
}
