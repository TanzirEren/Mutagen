package com.mutagen.lab;

import com.mutagen.lab.core.Project;
import com.mutagen.lab.core.SmaliInjector;

final class SmaliCleanup {
    private SmaliCleanup() {}
    static void clear(Project p) { SmaliInjector.injectionsFile(p).delete(); }
}
