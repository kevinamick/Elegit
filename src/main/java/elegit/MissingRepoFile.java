package elegit;

import org.apache.http.annotation.ThreadSafe;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A subclass of RepoFile that contains a file that Git reports as missing.
 *
 * This class is a view, controller, and model all mixed in one. That said. the model aspects are minimal, and the
 * view is mostly just a button and a context menu. Most notably, because most of the code is view oriented, ALL OF IT
 * should only be run from the JavaFX thread. In principle, a handful of method could be run elsewhere, but they're
 * fast anyway and not much would be gained; and most of this is FX work that should be done on the thread.
 *
 */
@ThreadSafe
// but only threadsafe because of the asserts on the FX thread nearly everywhere. No guarantees if any of those go;
// this needs to be thought through
public class MissingRepoFile extends RepoFile {

    MissingRepoFile(Path filePath, RepoHelper repo) {
        super(filePath, repo);
        Main.assertFxThread();
        setTextIdTooltip("MISSING", "missingDiffButton", "This file is missing.");
    }

    MissingRepoFile(String filePathString, RepoHelper repo) {
        this(Paths.get(filePathString), repo);
        Main.assertFxThread();
    }

    @Override public boolean canAdd() {
        Main.assertFxThread();
        return false;
    }

    @Override public boolean canRemove() {
        Main.assertFxThread();
        return true;
    }
}
