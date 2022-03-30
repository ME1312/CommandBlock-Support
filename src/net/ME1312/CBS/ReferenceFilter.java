package net.ME1312.CBS;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import static net.ME1312.CBS.EmulationManager.reference;
import static org.apache.logging.log4j.core.Filter.Result.DENY;
import static org.apache.logging.log4j.core.Filter.Result.NEUTRAL;

final class ReferenceFilter extends AbstractFilter {
    private static boolean registered = false;

    static void register() {
        if (!registered) {
            registered = true;
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
            if (type == reference) return DENY;
        }
        return NEUTRAL;
    }
}
