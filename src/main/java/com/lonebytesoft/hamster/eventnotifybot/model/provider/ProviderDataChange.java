package com.lonebytesoft.hamster.eventnotifybot.model.provider;

public sealed interface ProviderDataChange<T> {

    record Create<T>(
            T newData
    ) implements ProviderDataChange<T> {
    }

    record Update<T>(
            T oldData,
            T newData
    ) implements ProviderDataChange<T> {
    }

    record Delete<T>(
            T oldData
    ) implements ProviderDataChange<T> {
    }

}
