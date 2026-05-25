package com.lonebytesoft.hamster.eventnotifybot.service.provider;

import java.util.List;

public interface Provider<T> {

    List<T> get();

}
