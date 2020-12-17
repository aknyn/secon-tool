/*
 * Copyright © 2020 Techniker Krankenkasse
 * Copyright © 2020 BITMARCK Service GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tk.opensource.secon;

import global.namespace.fun.io.api.function.XFunction;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.function.Function;

import static de.tk.opensource.secon.SECON.directory;
import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static reactor.core.scheduler.Schedulers.newParallel;

@Tag("LDAP")
public class LdapConnectionTest {

    private static final Duration timeout = Duration.ofSeconds(3);

    // See https://kkv.gkv-diga.de as of December 17th, 2020, plus non-existing 999_999_999:
    private static final Flux<String> ids = Flux.just(
            101_097_008, 101_520_078, 101_570_104, 101_575_519, 102_110_939,
            102_171_012, 103_119_199, 103_411_401, 104_212_505, 104_526_376,
            104_940_005, 105_810_615, 107_299_005, 107_310_373, 108_018_007,
            108_310_400, 109_519_005, 109_905_003, 660_500_345, 999_999_999
    ).map(id -> String.format(ENGLISH, "%09d", id));

    private static final int idCount = requireNonNull(ids.count().block()).intValue();

    private final Directory dir = directory(URI.create("ldap://localhost"));

    @Test
    void sequentialCertCount() {
        assertTimeoutPreemptively(
                timeout,
                () -> StepVerifier.create(countCertsSequential()).expectNextCount(1).verifyComplete()
        );
    }

    @Test
    void parallelCertCount() {
        assertTimeoutPreemptively(
                timeout,
                () -> StepVerifier.create(countCertsParallel()).expectNextCount(1).verifyComplete()
        );
    }

    private Mono<Long> countCertsSequential() {
        return ids
                .flatMap(this::cert)
                .count();
    }

    private Mono<Long> countCertsParallel() {
        return ids
                .parallel(idCount)
                .runOn(newParallel("ldap", idCount, true))
                .flatMap(this::cert)
                .sequential()
                .count();
    }

    private Mono<X509Certificate> cert(String identifier) {
        return Mono
                .just(identifier)
                .flatMap(unchecked(dir::certificate))
                .flatMap(Mono::justOrEmpty);
    }

    private static <T, R> Function<T, Mono<R>> unchecked(XFunction<T, R> f) {
        return t -> Mono.fromCallable(() -> f.apply(t));
    }
}
