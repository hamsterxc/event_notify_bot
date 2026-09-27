package com.lonebytesoft.hamster.eventnotifybot.test.mock;

import com.lonebytesoft.hamster.eventnotifybot.model.provider.ProviderView;
import com.lonebytesoft.hamster.eventnotifybot.service.provider.Provider;

import java.util.Optional;

public record ProviderMock(
        String name,
        String data,
        String imageUrl,
        String view
) implements Provider {

    @Override
    public Optional<String> getData() {
        return Optional.of(data);
    }

    @Override
    public Optional<ProviderView> getStateView(String data, Integer limit, Integer limitWithImage) {
        return Optional.of(new ProviderView(imageUrl, view));
    }

    @Override
    public Optional<ProviderView> getDiffView(String baseData, String newData, Integer limit, Integer limitWithImage) {
        return Optional.of(new ProviderView(imageUrl, view));
    }

}
