package alpine.auth;

import java.security.Principal;
import javax.annotation.Nullable;
import javax.naming.AuthenticationException;

public class OpenIdConnectAuthticationService implements AuthenticationService {

  @Override
  public boolean isSpecified() {
    return false;
  }

  @Nullable
  @Override
  public Principal authenticate() throws AuthenticationException {
    return null;
  }
}
