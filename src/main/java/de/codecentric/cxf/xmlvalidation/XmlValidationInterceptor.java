package de.codecentric.cxf.xmlvalidation;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.UnmarshalException;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.interceptor.AbstractSoapInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.http.AbstractHTTPDestination;

import com.ctc.wstx.exc.WstxException;
import com.ctc.wstx.exc.WstxUnexpectedCharException;

import de.codecentric.cxf.common.FaultType;
import de.codecentric.cxf.logging.BaseLogger;

/**
 * Apache CXF Interceptor, which is processed early in the Interceptor-Chain,
 * that tries to analyze and handle all XML schema valdiation errors that could
 * occur somewhere in Apache CXF´s SOAP-Processing. Refers to the
 * {@link SoapFaultBuilder} to build a custom Soap-Fault, when
 * {@link CustomFaultBuilder} is implemented and configured.
 * 
 * @author Jonas Hecht
 *
 */
public class XmlValidationInterceptor extends AbstractSoapInterceptor {

	private static final String NO_BINDDING_ERROR_MESSAGE = "No binding operation info while invoking unknown method with params unknown.";

	private static final String MATCH_TO_OLD_BAD_WRONG_AXIS1 = "<h1>QBWebService</h1>\n"
			+ "<p>Hi there, this is an AXIS service!</p>\n"
			+ "<i>Perhaps there will be a form for invoking the service here...</i>";

	private static final BaseLogger LOG = BaseLogger.getLogger(XmlValidationInterceptor.class);
	private SoapFaultBuilder soapFaultBuilder;

	public XmlValidationInterceptor() {
		super(Phase.PRE_STREAM);
	}

	@Override
	public void handleMessage(SoapMessage soapMessage) throws Fault {
		Fault fault = (Fault) soapMessage.getContent(Exception.class);

		Throwable faultCause = fault.getCause();
		String faultMessage = fault.getMessage();

		if (containsFaultIndicatingNotSchemeCompliantXml(faultCause, faultMessage)) {
			LOG.schemaValidationError(FaultType.SCHEME_VALIDATION_ERROR, faultMessage);
			soapFaultBuilder.buildCustomFaultAndSet2SoapMessage(soapMessage, FaultType.SCHEME_VALIDATION_ERROR);
		} else if (containsFaultIndicatingSyntacticallyIncorrectXml(faultCause)) {
			LOG.schemaValidationError(FaultType.SYNTACTICALLY_INCORRECT_XML_ERROR, faultMessage);
			soapFaultBuilder.buildCustomFaultAndSet2SoapMessage(soapMessage,
					FaultType.SYNTACTICALLY_INCORRECT_XML_ERROR);
		} else if (nobindingIssue(faultMessage)) {

			HttpServletResponse response = (HttpServletResponse) soapMessage.getExchange().getInMessage()
					.get(AbstractHTTPDestination.HTTP_RESPONSE);
			response.setStatus(200);
			try {
				response.getOutputStream().write(MATCH_TO_OLD_BAD_WRONG_AXIS1.getBytes());
				response.getOutputStream().flush();
				soapMessage.getInterceptorChain().abort();
			} catch (IOException ioex) {
				throw new RuntimeException("Error writing the response");
			}

		} else if (someOtherErrorOccured(faultCause)) {
			// Some other Error occured, we don´t know. But we want to react with a Custom
			// Error-Message
			LOG.errorOccuredInBackendProcessing(faultCause);
			soapFaultBuilder.buildCustomFaultAndSet2SoapMessage(soapMessage, FaultType.BACKEND_PROCESSING_FAILED);
		}
	}

	private boolean nobindingIssue(String faultMessage) {
		if (NO_BINDDING_ERROR_MESSAGE.equalsIgnoreCase(faultMessage)) {
			return true;
		}
		return false;
	}

	private boolean containsFaultIndicatingNotSchemeCompliantXml(Throwable faultCause, String faultMessage) {
		if (faultCause instanceof UnmarshalException
				// 1.) If the root-Element of the SoapBody is syntactically correct, but not
				// scheme-compliant,
				// there is no UnmarshalException and we have to look for
				// 2.) Missing / lead to Faults without Causes, but to Messages like "Unexpected
				// wrapper element XYZ found. Expected"
				// One could argue, that this is syntactically incorrect, but here we just take
				// it as Non-Scheme-compliant
				|| isNotNull(faultMessage) && faultMessage.contains("Unexpected wrapper element")) {
			return true;
		}
		return false;
	}

	private boolean containsFaultIndicatingSyntacticallyIncorrectXml(Throwable faultCause) {
		if (faultCause instanceof WstxException
				// If Xml-Header is invalid, there is a wrapped Cause in the original Cause we
				// have to check
				|| isNotNull(faultCause) && faultCause.getCause() instanceof WstxUnexpectedCharException
				|| faultCause instanceof IllegalArgumentException) {
			return true;
		}
		return false;
	}

	private boolean someOtherErrorOccured(Throwable faultCause) {
		// Catch all other (un-)checked Exceptions, to handle with Custom Error-Message
		return isNotNull(faultCause);
	}

	private boolean isNotNull(Object object) {
		return object != null;
	}

	public void setSoapFaultBuilder(SoapFaultBuilder soapFaultBuilder) {
		this.soapFaultBuilder = soapFaultBuilder;
	}
}
