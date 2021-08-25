package com.tsystems.readyapi.plugin.websocket;

import com.eviware.soapui.impl.EmptyPanelBuilder;
import com.eviware.soapui.plugins.auto.PluginPanelBuilder;
import com.eviware.soapui.ui.desktop.DesktopPanel;

@PluginPanelBuilder(targetModelItem = ReceiveClosureTestStep.class)
public class ReceiveClosureTestStepPanelBuilder extends EmptyPanelBuilder<ReceiveClosureTestStep> {

    @Override
    public DesktopPanel buildDesktopPanel(ReceiveClosureTestStep testStep) {
        return new ReceiveClosureTestStepPanel(testStep);
    }

    @Override
    public boolean hasDesktopPanel() {
        return true;
    }
}
