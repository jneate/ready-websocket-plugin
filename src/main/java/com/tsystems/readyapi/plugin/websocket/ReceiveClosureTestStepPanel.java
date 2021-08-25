package com.tsystems.readyapi.plugin.websocket;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.util.Date;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ListModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import com.eviware.soapui.impl.wsdl.panels.teststeps.AssertionsPanel;
import com.eviware.soapui.model.testsuite.Assertable;
import com.eviware.soapui.model.testsuite.AssertionsListener;
import com.eviware.soapui.model.testsuite.TestAssertion;
import com.eviware.soapui.support.DateUtil;
import com.eviware.soapui.support.JsonUtil;
import com.eviware.soapui.support.ListDataChangeListener;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.components.JComponentInspector;
import com.eviware.soapui.support.components.JInspectorPanel;
import com.eviware.soapui.support.components.JInspectorPanelFactory;
import com.eviware.soapui.support.components.JXToolBar;
import com.eviware.soapui.support.components.SimpleBindingForm;
import com.eviware.soapui.support.log.JLogList;
import com.eviware.soapui.support.xml.SyntaxEditorUtil;
import com.eviware.soapui.support.xml.XmlUtils;
import com.jgoodies.binding.PresentationModel;
import com.jgoodies.binding.adapter.Bindings;

public class ReceiveClosureTestStepPanel extends ConnectedTestStepPanel<ReceiveClosureTestStep>
        implements AssertionsListener, ExecutionListener {

    /** serialVersionUID description. */
    private static final long serialVersionUID = 398715768048748119L;
    private final static String LOG_TAB_TITLE = "Test Step Log (%d)";
    private JComponentInspector<JComponent> assertionInspector;
    private JInspectorPanel inspectorPanel;
    private AssertionsPanel assertionsPanel;

    private JComponentInspector<JComponent> logInspector;

    private JLogList logArea;
    private RunTestStepAction startAction;

    public ReceiveClosureTestStepPanel(ReceiveClosureTestStep modelItem) {
        super(modelItem);
        buildUI();
        modelItem.addAssertionsListener(this);
        modelItem.addExecutionListener(this);
    }

    @Override
    public void afterExecution(ExecutableTestStep testStep, ExecutableTestStepResult executionResult) {
        logArea.addLine(
                DateUtil.formatFull(new Date(executionResult.getTimeStamp())) + " - " + executionResult.getOutcome());
    }

    @Override
    public void assertionAdded(TestAssertion assertion) {
        assertionListChanged();
    }

    private void assertionListChanged() {
        assertionInspector.setTitle(String.format("Assertions (%d)", getModelItem().getAssertionCount()));
    }

    @Override
    public void assertionMoved(TestAssertion assertion, int ix, int offset) {
        assertionListChanged();
    }

    @Override
    public void assertionRemoved(TestAssertion assertion) {
        assertionListChanged();
    }

    private AssertionsPanel buildAssertionsPanel() {
        return new AssertionsPanel(getModelItem());
    }

    protected JComponent buildLogPanel() {
        logArea = new JLogList("Test Step Log");

        logArea.getLogList().getModel().addListDataListener(new ListDataChangeListener() {

            @Override
            public void dataChanged(ListModel model) {
                logInspector.setTitle(String.format(LOG_TAB_TITLE, model.getSize()));
            }
        });

        return logArea;
    }

    private JComponent buildMainPanel() {
        PresentationModel<ReceiveClosureTestStep> pm = new PresentationModel<ReceiveClosureTestStep>(getModelItem());
        SimpleBindingForm form = new SimpleBindingForm(pm);
        buildConnectionSection(form, pm);
        form.appendSeparator();
        form.appendHeading("Receiver Settings");
        buildTimeoutSpinEdit(form, pm, "Timeout");
        form.appendSeparator();
        form.appendHeading("Received Closure");
        form.appendTextField("closureMessage", "Closing Message", "WebSocket Closing Message").setEditable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
        mainPanel.add(buildToolbar(), BorderLayout.NORTH);
        mainPanel.add(new JScrollPane(form.getPanel(), ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);
        return mainPanel;
    }

    private JComponent buildToolbar() {
        JXToolBar toolBar = UISupport.createToolbar();
        startAction = new RunTestStepAction(getModelItem());
        JButton submitButton = UISupport.createActionButton(startAction, startAction.isEnabled());
        toolBar.add(submitButton);
        submitButton.setMnemonic(KeyEvent.VK_ENTER);
        toolBar.add(UISupport.createActionButton(startAction.getCorrespondingStopAction(),
                startAction.getCorrespondingStopAction().isEnabled()));
        addConnectionActionsToToolbar(toolBar);
        return toolBar;
    }

    private void buildUI() {

        JComponent mainPanel = buildMainPanel();
        inspectorPanel = JInspectorPanelFactory.build(mainPanel);

        assertionsPanel = buildAssertionsPanel();

        assertionInspector = new JComponentInspector<JComponent>(assertionsPanel,
                "Assertions (" + getModelItem().getAssertionCount() + ")", "Assertions for this Message", true);

        inspectorPanel.addInspector(assertionInspector);

        logInspector = new JComponentInspector<JComponent>(buildLogPanel(), String.format(LOG_TAB_TITLE, 0),
                "Log of the test step executions", true);
        inspectorPanel.addInspector(logInspector);

        inspectorPanel.setDefaultDividerLocation(0.6F);
        inspectorPanel.setCurrentInspector("Assertions");

        updateStatusIcon();

        add(inspectorPanel.getComponent());

        // propertyChange(
        //         new PropertyChangeEvent(getModelItem(), "receivedMessage", null, getModelItem().getReceivedMessage()));

    }

    @Override
    public boolean release() {
        startAction.cancel();
        getModelItem().removeExecutionListener(this);
        getModelItem().removeAssertionsListener(this);
        assertionsPanel.release();
        inspectorPanel.release();

        return super.release();
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        super.propertyChange(event);
        if (event.getPropertyName().equals("assertionStatus")) {
            updateStatusIcon();
        }
    }

    private void updateStatusIcon() {
        Assertable.AssertionStatus status = getModelItem().getAssertionStatus();
        switch (status) {
        case FAILED: {
            assertionInspector.setIcon(UISupport.createImageIcon("/failed_assertion.gif"));
            inspectorPanel.activate(assertionInspector);
            break;
        }
        case UNKNOWN: {
            assertionInspector.setIcon(UISupport.createImageIcon("/unknown_assertion.png"));
            break;
        }
        case VALID: {
            assertionInspector.setIcon(UISupport.createImageIcon("/valid_assertion.gif"));
            inspectorPanel.deactivate();
            break;
        }
        }
    }
}
