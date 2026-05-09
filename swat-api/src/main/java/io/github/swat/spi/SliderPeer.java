package io.github.swat.spi;

import java.util.function.DoubleConsumer;

public non-sealed interface SliderPeer extends Peer {
    void setRange(double min, double max);
    void setValue(double value);
    double getValue();
    void onValueChange(DoubleConsumer trigger);
}
