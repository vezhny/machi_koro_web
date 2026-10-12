package com.machikoro.server.game;

import com.machikoro.core.engine.SeededRandomSource;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class GameConfig {

    /** Reproducible dice from a per-game random seed. */
    @Bean
    @ConditionalOnMissingBean
    DiceFactory diceFactory() {
        return SeededRandomSource::new;
    }

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }
}
