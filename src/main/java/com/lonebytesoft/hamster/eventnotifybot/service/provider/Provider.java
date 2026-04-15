package com.lonebytesoft.hamster.eventnotifybot.service.provider;

import com.lonebytesoft.hamster.eventnotifybot.model.provider.Fingerprintable;

import java.util.List;

public interface Provider<T extends Fingerprintable> {

    List<T> get();

}
