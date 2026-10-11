package fi.alavesa.cutscenes.camera;

import fi.alavesa.cutscenes.CutsceneRun;

/**
 * One camera "shot" of a cutscene: given the running cutscene and how many ticks the shot has been
 * active, where is the camera and where does it look. A shot stays active until the next camera event.
 */
public interface CameraShot {

    /** @param localTick ticks since this shot started (0 on its first frame). */
    Pose pose(CutsceneRun run, int localTick);

    /** A short human-readable description for /cutscene info. */
    String describe();
}
