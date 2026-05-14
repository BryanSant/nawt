package cc.nawt;

public sealed interface Container extends Widget
    permits Window, Column, Row, Frame, ScrollContainer, Tabs, Splitter, Expander, Grid, NavigationSplit, Overlay {}
