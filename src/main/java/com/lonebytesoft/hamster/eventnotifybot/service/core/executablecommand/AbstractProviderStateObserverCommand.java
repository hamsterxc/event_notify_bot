package com.lonebytesoft.hamster.eventnotifybot.service.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.model.core.ProviderState;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;

import java.time.InstantSource;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

abstract class AbstractProviderStateObserverCommand implements ExecutableCommand {

    private static final long PROVIDER_STATE_UPDATE_BUFFER_SECONDS = TimeUnit.MINUTES.toSeconds(1);

    private final InstantSource instantSource;

    protected AbstractProviderStateObserverCommand(
            final InstantSource instantSource
    ) {
        this.instantSource = instantSource;
    }

    /**
     * Return provider state if it is stored and not stale.
     */
    protected Optional<String> getProviderData(
            final StorageService storageService,
            final String providerName
    ) {
        return getProviderState(storageService, providerName)
                .filter(providerState -> isActualState(providerState, 0))
                .map(ProviderState::data);
    }

    /**
     * Return provider state if it is stored and will not be stale for at least {@link #PROVIDER_STATE_UPDATE_BUFFER_SECONDS} seconds
     * to accommodate for any delays between checks and actual execution.
     * TODO: embed checks into the execution process itself.
     */
    protected Optional<String> getProviderDataWithBuffer(
            final StorageService storageService,
            final String providerName
    ) {
        return getProviderState(storageService, providerName)
                .filter(providerState -> isActualState(providerState, PROVIDER_STATE_UPDATE_BUFFER_SECONDS))
                .map(ProviderState::data);
    }

    private Optional<ProviderState> getProviderState(
            final StorageService storageService,
            final String providerName
    ) {
        return storageService.getProviderStates()
                .stream()
                .filter(providerState -> Objects.equals(providerName, providerState.provider()))
                .max(Comparator.comparing(ProviderState::time, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    private boolean isActualState(
            final ProviderState providerState,
            final long bufferSeconds
    ) {
        final Long time = providerState.time();
        return (time == null)
                || (instantSource.instant().getEpochSecond() + bufferSeconds < time);
    }

}
