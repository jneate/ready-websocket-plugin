package com.tsystems.readyapi.plugin.websocket;

import static com.tsystems.readyapi.plugin.websocket.Utils.bytesToHexString;

import java.beans.PropertyChangeEvent;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.config.TestAssertionConfig;
import com.eviware.soapui.config.TestStepConfig;
import com.eviware.soapui.impl.wsdl.WsdlSubmitContext;
import com.eviware.soapui.impl.wsdl.support.AbstractNonHttpMessageExchange;
import com.eviware.soapui.impl.wsdl.support.IconAnimator;
import com.eviware.soapui.impl.wsdl.support.assertions.AssertableConfig;
import com.eviware.soapui.impl.wsdl.support.assertions.AssertionsSupport;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlMessageAssertion;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestStepResult;
import com.eviware.soapui.impl.wsdl.teststeps.assertions.TestAssertionRegistry;
import com.eviware.soapui.model.iface.Interface;
import com.eviware.soapui.model.iface.Response;
import com.eviware.soapui.model.iface.SubmitContext;
import com.eviware.soapui.model.support.DefaultTestStepProperty;
import com.eviware.soapui.model.support.TestStepBeanProperty;
import com.eviware.soapui.model.testsuite.Assertable;
import com.eviware.soapui.model.testsuite.AssertionsListener;
import com.eviware.soapui.model.testsuite.TestAssertion;
import com.eviware.soapui.model.testsuite.TestCaseRunContext;
import com.eviware.soapui.model.testsuite.TestCaseRunner;
import com.eviware.soapui.model.testsuite.TestStep;
import com.eviware.soapui.model.testsuite.TestStepResult;
import com.eviware.soapui.monitor.TestMonitor;
import com.eviware.soapui.plugins.auto.PluginTestStep;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.xml.XmlObjectConfigurationReader;
import com.google.common.base.Charsets;
import org.glassfish.tyrus.core.WebSocketException;

@PluginTestStep(typeName = "WebsocketReceiveClosureTestStep", name = "Receive Websocket Closure Event",
        description = "Waits for a Websocket Closure Event.",
        iconPath = "com/tsystems/readyapi/plugin/websocket/receive_step.png")
public class ReceiveClosureTestStep extends ConnectedTestStep implements Assertable {
    private final static Logger LOGGER = Logger.getLogger(PluginConfig.LOGGER_NAME);

    private final static String CLOSURE_MESSAGE_PROP_NAME = "ClosureMessage";
    private final static String ASSERTION_SECTION = "assertion";

    private static boolean actionGroupAdded = false;

    private String closureMessage = null;

    private AssertionsSupport assertionsSupport;
    private AssertionStatus assertionStatus = AssertionStatus.UNKNOWN;
    private ArrayList<TestAssertionConfig> assertionConfigs = new ArrayList<TestAssertionConfig>();
    private ImageIcon validStepIcon;

    private ImageIcon failedStepIcon;
    private ImageIcon disabledStepIcon;
    private ImageIcon unknownStepIcon;
    private IconAnimator<ReceiveClosureTestStep> iconAnimator;

    private MessageExchangeImpl messageExchange;

    public ReceiveClosureTestStep(WsdlTestCase testCase, TestStepConfig config, boolean forLoadTest) {
        super(testCase, config, true, forLoadTest);
        if (!actionGroupAdded) {
            SoapUI.getActionRegistry().addActionGroup(new ReceiveClosureTestStepActionGroup());
            actionGroupAdded = true;
        }
        if ((config != null) && (config.getConfig() != null)) {
            XmlObjectConfigurationReader reader = new XmlObjectConfigurationReader(config.getConfig());
            readData(reader);
        }
        initAssertions(config);

        addProperty(new DefaultTestStepProperty(TIMEOUT_PROP_NAME, false,
                new DefaultTestStepProperty.PropertyHandler() {
                    @Override
                    public String getValue(DefaultTestStepProperty property) {
                        return Integer.toString(getTimeout());
                    }

                    @Override
                    public void setValue(DefaultTestStepProperty property, String value) {
                        int newValue;
                        try {
                            newValue = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            return;
                        }
                        setTimeout(newValue);
                    }

                }, this));

        addProperty(new TestStepBeanProperty(CLOSURE_MESSAGE_PROP_NAME, true, this, "closureMessage", this));

        if (!forLoadTest)
            initIcons();

        messageExchange = new MessageExchangeImpl(this);

        updateState();
    }

    @Override
    public TestAssertion addAssertion(String selection) {

        try {
            WsdlMessageAssertion assertion = assertionsSupport.addWsdlAssertion(selection);
            if (assertion == null)
                return null;

            if (closureMessage != null) {
                applyAssertion(assertion, new WsdlSubmitContext(this));
                updateState();
            }

            return assertion;
        } catch (Exception e) {
            LOGGER.error(e);
            throw e;
        }
    }

    @Override
    public void addAssertionsListener(AssertionsListener listener) {
        assertionsSupport.addAssertionsListener(listener);
    }

    private void applyAssertion(WsdlMessageAssertion assertion, SubmitContext context) {
        assertion.assertProperty(this, CLOSURE_MESSAGE_PROP_NAME, messageExchange, context);
    }

    private void assertClosureMessage() {
        if (getClosureMessage() != null)
            for (WsdlMessageAssertion assertion : assertionsSupport.getAssertionList())
                applyAssertion(assertion, new WsdlSubmitContext(this));
        updateState();
    }

    @Override
    public TestAssertion cloneAssertion(TestAssertion source, String name) {
        return assertionsSupport.cloneAssertion(source, name);
    }

    @Override
    protected ExecutableTestStepResult doExecute(SubmitContext runContext, CancellationToken cancellationToken) {
        ExecutableTestStepResult result = new ExecutableTestStepResult(this);
        result.startTimer();
        result.setStatus(TestStepResult.TestStepStatus.UNKNOWN);
        if (iconAnimator != null)
            iconAnimator.start();
        try {

            Client client = getClient(runContext, result);
            if (client == null) {
                result.setStatus(TestStepResult.TestStepStatus.FAILED);
                result.setError(new Exception("No WebSocket Client found"));
                return result;
            }

            if (client.isFaulty() || !client.isConnected()) {
                if (client.getClosureReason() != null) {
                    setClosureMessage("[" + client.getClosureReason().getCloseCode().getCode() + "] " + client.getClosureReason().getReasonPhrase());
                    for (WsdlMessageAssertion assertion : assertionsSupport.getAssertionList()) {
                        applyAssertion(assertion, runContext);
                        if (assertion.isFailed()) {
                            result.setStatus(TestStepResult.TestStepStatus.FAILED);
                            return result;
                        }
                    }
                } else {
                    result.setStatus(TestStepResult.TestStepStatus.FAILED);
                    result.setError(new Exception("WebSocket closed but unable to find closing reason"));
                    return result;
                }
                result.setStatus(TestStepResult.TestStepStatus.OK);
                return result;
            } else {
                result.setStatus(TestStepResult.TestStepStatus.FAILED);
                result.setError(new Exception("WebSocket still open and healthy"));
                return result;
            }
        } finally {
            if (iconAnimator != null)
            iconAnimator.stop();
            updateState();
            if (result.getStatus() == TestStepResult.TestStepStatus.UNKNOWN) {
                assertClosureMessage();
                
                switch (getAssertionStatus()) {
                    case FAILED:
                    result.setStatus(TestStepResult.TestStepStatus.FAILED);
                    break;
                    case VALID:
                    result.setStatus(TestStepResult.TestStepStatus.OK);
                    break;
                }
            }
            result.stopTimer();
            result.setOutcome(formOutcome(result));
            SoapUI.log(String.format("%s - [%s test step]", result.getOutcome(), getName()));
            notifyExecutionListeners(result);
        }

    }

    private String formOutcome(ExecutableTestStepResult executionResult) {
        if (executionResult.getStatus() == TestStepResult.TestStepStatus.CANCELED) {
            return "CANCELED";
        } else if (executionResult.getStatus() == TestStepResult.TestStepStatus.FAILED) {
            if (executionResult.getError() == null) {
                return "WebSocket Closed but assertion(s) failed";
            } else {
                return String.format("Error - %s", executionResult.getError().getMessage());
            }
        } else
            return String.format("WebSocket Closed - %d", executionResult.getTimeTaken());

    }

    @Override
    public String getAssertableContent() {
        return getClosureMessage();
    }

    @Override
    public String getAssertableContentAsXml() {
        return getClosureMessage();
    }

    @Override
    public TestAssertionRegistry.AssertableType getAssertableType() {
        return TestAssertionRegistry.AssertableType.BOTH;
    }

    @Override
    public TestAssertion getAssertionAt(int c) {
        return assertionsSupport.getAssertionAt(c);
    }

    @Override
    public TestAssertion getAssertionByName(String name) {
        return assertionsSupport.getAssertionByName(name);
    }

    @Override
    public int getAssertionCount() {
        return assertionsSupport.getAssertionCount();
    }

    @Override
    public List<TestAssertion> getAssertionList() {
        return new ArrayList<TestAssertion>(assertionsSupport.getAssertionList());
    }

    @Override
    public Map<String, TestAssertion> getAssertions() {
        return assertionsSupport.getAssertions();
    }

    @Override
    public AssertionStatus getAssertionStatus() {
        return assertionStatus;
    }

    @Override
    public String getDefaultAssertableContent() {
        return "";
    }

    @Override
    public Interface getInterface() {
        return null;
    }

    public String getClosureMessage() {
        return closureMessage;
    }

    @Override
    public TestStep getTestStep() {
        return this;
    }

    private void initAssertions(TestStepConfig testStepData) {
        if (testStepData != null && testStepData.getConfig() != null) {
            XmlObject config = testStepData.getConfig();
            XmlObject[] assertionsSections = config.selectPath("$this/" + ASSERTION_SECTION);
            for (XmlObject assertionSection : assertionsSections) {
                TestAssertionConfig assertionConfig;
                try {
                    assertionConfig = TestAssertionConfig.Factory.parse(assertionSection.toString());
                } catch (XmlException e) {
                    LOGGER.error(e);
                    continue;
                }
                assertionConfigs.add(assertionConfig);
            }
        }
        assertionsSupport = new AssertionsSupport(this, new AssertableConfigImpl());
    }

    protected void initIcons() {
        validStepIcon = UISupport.createImageIcon("com/tsystems/readyapi/plugin/websocket/valid_receive_step.png");
        failedStepIcon = UISupport.createImageIcon("com/tsystems/readyapi/plugin/websocket/invalid_receive_step.png");
        unknownStepIcon = UISupport.createImageIcon("com/tsystems/readyapi/plugin/websocket/unknown_receive_step.png");
        disabledStepIcon = UISupport.createImageIcon("com/tsystems/readyapi/plugin/websocket/disabled_receive_step.png");

        iconAnimator = new IconAnimator<ReceiveClosureTestStep>(this, "com/tsystems/readyapi/plugin/websocket/receive_step_base.png",
                "com/tsystems/readyapi/plugin/websocket/receive_step.png", 5);
    }

    @Override
    public TestAssertion moveAssertion(int ix, int offset) {
        WsdlMessageAssertion assertion = assertionsSupport.getAssertionAt(ix);
        try {
            return assertionsSupport.moveAssertion(ix, offset);
        } finally {
            assertion.release();
            updateState();
        }
    }

    @Override
    public void prepare(TestCaseRunner testRunner, TestCaseRunContext testRunContext) throws Exception {
        super.prepare(testRunner, testRunContext);
        setClosureMessage(null);
        for (TestAssertion assertion : assertionsSupport.getAssertionList())
            assertion.prepare(testRunner, testRunContext);
    }

    @Override
    public ExecutableTestStepResult execute(SubmitContext runContext, CancellationToken cancellationToken) {
        setClosureMessage(null);
        return super.execute(runContext, cancellationToken);
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if (event.getPropertyName().equals(TestAssertion.CONFIGURATION_PROPERTY)
                || event.getPropertyName().equals(TestAssertion.DISABLED_PROPERTY)) {
            updateData();
            assertClosureMessage();
        }
    }

    @Override
    protected void readData(XmlObjectConfigurationReader reader) {
        super.readData(reader);
    }

    @Override
    public void removeAssertion(TestAssertion assertion) {
        try {
            assertionsSupport.removeAssertion((WsdlMessageAssertion) assertion);

        } finally {
            ((WsdlMessageAssertion) assertion).release();
        }
        updateState();
    }

    @Override
    public void removeAssertionsListener(AssertionsListener listener) {
        assertionsSupport.removeAssertionsListener(listener);
    }

    @Override
    public void setIcon(ImageIcon newIcon) {
        if (iconAnimator != null && newIcon == iconAnimator.getBaseIcon())
            return;
        super.setIcon(newIcon);
    }

    public void setClosureMessage(String value) {
        setProperty("closureMessage", CLOSURE_MESSAGE_PROP_NAME, value);
    }

    @Override
    protected void updateState() {
        final AssertionStatus oldAssertionStatus = assertionStatus;
        if (getClosureMessage() != null) {
            int cnt = getAssertionCount();
            if (cnt == 0)
                assertionStatus = AssertionStatus.UNKNOWN;
            else {
                assertionStatus = AssertionStatus.VALID;
                for (int c = 0; c < cnt; c++)
                    if (getAssertionAt(c).getStatus() == AssertionStatus.FAILED) {
                        assertionStatus = AssertionStatus.FAILED;
                        break;
                    }
            }
        } else
            assertionStatus = AssertionStatus.UNKNOWN;
        if (oldAssertionStatus != assertionStatus) {
            final AssertionStatus newAssertionStatus = assertionStatus;
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    notifyPropertyChanged("assertionStatus", oldAssertionStatus, newAssertionStatus);
                }
            });
        }
        if (iconAnimator == null)
            return;
        TestMonitor testMonitor = SoapUI.getTestMonitor();
        if ((testMonitor != null)
                && (testMonitor.hasRunningLoadTest(getTestStep().getTestCase()) || testMonitor
                        .hasRunningSecurityTest(getTestStep().getTestCase())))
            setIcon(disabledStepIcon);
        else {
            ImageIcon icon = iconAnimator.getIcon();
            if (icon == iconAnimator.getBaseIcon())
                switch (assertionStatus) {
                case VALID:
                    setIcon(validStepIcon);
                    break;
                case FAILED:
                    setIcon(failedStepIcon);
                    break;
                case UNKNOWN:
                    setIcon(unknownStepIcon);
                    break;
                }
        }
    }

    @Override
    protected void writeData(XmlObjectBuilder builder) {
        super.writeData(builder);
        for (TestAssertionConfig assertionConfig : assertionConfigs)
            builder.addSection(ASSERTION_SECTION, assertionConfig);
    }

    private class AssertableConfigImpl implements AssertableConfig {

        @Override
        public TestAssertionConfig addNewAssertion() {
            TestAssertionConfig newConfig = TestAssertionConfig.Factory.newInstance();
            assertionConfigs.add(newConfig);
            return newConfig;
        }

        @Override
        public List<TestAssertionConfig> getAssertionList() {
            return assertionConfigs;
        }

        @Override
        public TestAssertionConfig insertAssertion(TestAssertionConfig source, int ix) {
            TestAssertionConfig conf = TestAssertionConfig.Factory.newInstance();
            conf.set(source);
            assertionConfigs.add(ix, conf);
            updateData();
            return conf;
        }

        @Override
        public void removeAssertion(int ix) {
            assertionConfigs.remove(ix);
            updateData();
        }
    }

    private class MessageExchangeImpl extends AbstractNonHttpMessageExchange<ReceiveClosureTestStep> {

        public MessageExchangeImpl(ReceiveClosureTestStep modelItem) {
            super(modelItem);
        }

        @Override
        public String getEndpoint() {
            return null;
        }

        @Override
        public boolean hasRequest(boolean ignoreEmpty) {
            return false;
        }

        @Override
        public boolean hasResponse() {
            return false;
        }

        @Override
        public Response getResponse() {
            return null;
        }

        @Override
        public String getRequestContent() {
            return null;
        }

        @Override
        public String getResponseContent() {
            return null;
        }

        @Override
        public long getTimeTaken() {
            return 0;
        }

        @Override
        public long getTimestamp() {
            return System.currentTimeMillis();
        }

        @Override
        public boolean isDiscarded() {
            return false;
        }

    }

}
