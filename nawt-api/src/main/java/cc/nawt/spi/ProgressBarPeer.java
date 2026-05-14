package cc.nawt.spi;

public non-sealed interface ProgressBarPeer extends Peer {
    void setValue(double value);
    void setIndeterminate(boolean indeterminate);
}
