package de.codecentric.cxf.xmlvalidation;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.interceptor.AbstractSoapInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.http.AbstractHTTPDestination;

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

	public XmlValidationInterceptor() {
		super(Phase.PRE_STREAM);
	}

	@Override
	public void handleMessage(SoapMessage soapMessage) throws Fault {
		Fault fault = (Fault) soapMessage.getContent(Exception.class);

		String faultMessage = fault.getMessage();

		if (nobindingIssue(faultMessage)) {

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

		}
	}

	private boolean nobindingIssue(String faultMessage) {
		if (NO_BINDDING_ERROR_MESSAGE.equalsIgnoreCase(faultMessage)) {
			return true;
		}
		return false;
	}

}
