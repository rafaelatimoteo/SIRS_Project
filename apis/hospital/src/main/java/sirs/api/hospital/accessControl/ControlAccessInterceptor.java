package sirs.api.hospital.accessControl;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import sirs.api.hospital.Crypto;
import sirs.api.hospital.db.Repo;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

public class ControlAccessInterceptor implements HandlerInterceptor {
    final private String pdpUrl = "http://192.168.56.12/pdp";
    final private String authenticationHeader = "Authorization";
    Repo db = new Repo();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Build a request describer
        RequestDescriber req = buildRequestDescriber(request.getRequestURI(), request.getMethod(), handler);

        // Asked for nonexistent resource, small optimization to avoid a couple trips when the answer is known
        if(req.getResourceId().equals(""))
            return false;

        // If the resource trying to access is not the login endpoint it has to do extra work
        // like checking if the authentication token is valid and if so associate that information
        // with the request.
        if(!req.getResourceId().equals("login")) {
            // Get access token from the 'Authorization' header.
            String accessToken = request.getHeader(authenticationHeader);

            // Not authorized to access any resource if subject is not authenticated & wants to access a resource
            // different from the login endpoint.
            if(accessToken == null)
                return false;

            // Validates the authenticity of the token, if role is empty then there is no session
            // with this token.
            String role = validateToken(accessToken);
            if(role.equals(""))
                return false;

            req.setRole(role);
        }

        // Builds the XACMLRequest, forwards it to the PDP and if authorized lets request continue
        XACMLRequest xreq = buildxacmlreq(req);
        return checkAuthorization(xreq);
    }

    /**
     *  Forwards a XACMLRequest to the "Policy Decision Point" and awaits an answer
     *  if the request is allowed to continue it will return true.
     */
    private Boolean checkAuthorization(XACMLRequest xreq) {
        return true;
    }

    /**
     *  Build's a request with the format specified by the XACML Framework.
     */
    private XACMLRequest buildxacmlreq(RequestDescriber req) {
        return null;
    }

    // Check if the token is valid and returns the associated role
    private String validateToken(String accessToken) {
        return db.validateToken(accessToken);
    }

    /**
     * Builds an object that describes the request (method, resource...)
     * this info will be used to build the XACML Request.
     *
     * The most important info in this method is the "ResourceId" which associates
     * a resource with a identification which will be used to check the policies.
     */
    private RequestDescriber buildRequestDescriber(String reqUri, String method, Object handler) {
        try {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            Method mh = handlerMethod.getMethod();
            ResourceId resourceIdentifierAnnotation = mh.getAnnotation(ResourceId.class);
            String resourceName = resourceIdentifierAnnotation.resourceId();

            return new RequestDescriber(resourceName, method, reqUri);
        } catch(Exception e) {
            System.out.println("Method has no annotation identifying the resource...");
            return new RequestDescriber("", method, reqUri);
        }
    }
}
