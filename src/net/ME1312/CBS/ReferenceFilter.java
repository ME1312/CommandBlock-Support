package net.ME1312.CBS;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import static org.apache.logging.log4j.core.Filter.Result.*;

final class ReferenceFilter extends AbstractFilter {
    private static boolean registered = false;
    private static RuntimeException filtered;

    static void register(RuntimeException reference) {
        if (!registered) {
            registered = true;
            filtered = reference;
            ((Logger) LogManager.getRootLogger()).addFilter(new ReferenceFilter());
        }
    }

    private ReferenceFilter() {
        super(DENY, NEUTRAL);
        start();
    }

    @Override
    public Result filter(LogEvent event) {
        for (Throwable type = event.getThrown(); type != null; type = type.getCause()) {
            if (type == filtered) return DENY;
        }
        return NEUTRAL;
    }
}
