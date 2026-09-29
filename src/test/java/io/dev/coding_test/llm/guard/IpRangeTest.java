package io.dev.coding_test.llm.guard;

import io.dev.coding_test.common.util.IpAddressUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IpRangeTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "100.64.0.0/10       | 100.64.0.1         | true",
            "100.64.0.0/10       | 100.127.255.255    | true",
            "100.64.0.0/10       | 100.128.0.0        | false",
            "100.64.0.0/10       | 100.63.255.255     | false",
            "192.168.0.0/24      | 192.168.0.255      | true",
            "192.168.0.0/24      | 192.168.1.0        | false",
            "192.168.0.10        | 192.168.0.10       | true",
            "192.168.0.10        | 192.168.0.11       | false",
            "0.0.0.0/0           | 10.1.2.3           | true",
            "fd7a:115c:a1e0::/48 | fd7a:115c:a1e0::1  | true",
            "fd7a:115c:a1e0::/48 | fd7a:115c:a1e1::1  | false",
            "100.64.0.0/10       | fd7a:115c:a1e0::1  | false",
            "100.64.0.0/10       | ::ffff:100.66.0.1  | true",
    })
    void 주소가_대역에_속하는지_판단한다(String cidr, String address, boolean expected) {
        InetAddress target = IpAddressUtil.parseLiteral(address).orElseThrow();

        assertThat(IpRange.parse(cidr).contains(target)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "100.64.0.0/33", "100.64.0.0/-1", "100.64.0.0/abc", "example.com/24", "fd00::/129"})
    void 형식이_틀린_대역은_거부한다(String cidr) {
        assertThatThrownBy(() -> IpRange.parse(cidr)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "100.64.0.0/10 | 100.64.0.0/10       | true",
            "100.64.0.0/10 | 100.100.0.0/16      | true",
            "100.64.0.0/10 | 100.66.180.73       | true",
            "100.64.0.0/10 | 100.0.0.0/8         | false",
            "100.64.0.0/10 | 192.168.0.0/16      | false",
            "100.64.0.0/10 | fd7a:115c:a1e0::/48 | false",
    })
    void 다른_대역이_이_대역_안에_들어가는지_판단한다(String outer, String inner, boolean expected) {
        assertThat(IpRange.parse(outer).containsRange(IpRange.parse(inner))).isEqualTo(expected);
    }
}
