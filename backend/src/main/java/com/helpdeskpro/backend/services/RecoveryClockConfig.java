package com.helpdeskpro.backend.services;
import org.springframework.context.annotation.*;
import java.time.Clock;
@Configuration public class RecoveryClockConfig {
 @Bean public Clock recoveryClock(){return Clock.systemUTC();}
}
