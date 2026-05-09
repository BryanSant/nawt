package io.github.swat.spi;

public non-sealed interface ProgressBarPeer extends Peer {
    void setValue(double value);
    void setIndeterminate(boolean indeterminate);
}
