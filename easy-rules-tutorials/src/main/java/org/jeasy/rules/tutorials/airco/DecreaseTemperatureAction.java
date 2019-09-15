package org.jeasy.rules.tutorials.airco;

import org.jeasy.rules.api.Facts;

import java.util.function.Consumer;

public class DecreaseTemperatureAction implements Consumer<Facts> {

    static DecreaseTemperatureAction decreaseTemperature() {
        return new DecreaseTemperatureAction();
    }

    @Override
    public void accept(Facts facts) {
        System.out.println("It is hot! cooling air..");
        Integer temperature = facts.get("temperature");
        facts.put("temperature", temperature - 1);
    }
}
