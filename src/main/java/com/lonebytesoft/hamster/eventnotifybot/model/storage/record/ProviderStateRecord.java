package com.lonebytesoft.hamster.eventnotifybot.model.storage.record;

import com.lonebytesoft.hamster.eventnotifybot.model.core.ProviderState;

public record ProviderStateRecord(
        String id,
        ProviderState providerState
) {
}
