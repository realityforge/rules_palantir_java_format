package example;

import com.google.common.collect.ImmutableList;
import java.util.List;

final class Smoke {
    private final List<String> values = ImmutableList.of("smoke");

    List<String> values() {
        return values;
    }
}
