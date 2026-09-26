package com.lonebytesoft.hamster.eventnotifybot.service.provider;

import com.lonebytesoft.hamster.eventnotifybot.model.provider.ProviderView;

import java.util.Optional;

public interface Provider {

    Optional<String> getData();

    Optional<ProviderView> getStateView(String data, Integer limit, Integer limitWithImage);

    Optional<ProviderView> getDiffView(String baseData, String newData, Integer limit, Integer limitWithImage);

}
