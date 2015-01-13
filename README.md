[![Build Status](https://travis-ci.org/stevespringett/dependency-track.svg?branch=master)](https://travis-ci.org/stevespringett/dependency-track)

Dependency-Track
=========

OWASP Dependency-Track is a Java web application that allows organizations to
document the use of third-party components across multiple applications and
versions. Further, it provides automatic visibility into the use of components
with known vulnerabilities.

The OWASP Top Ten 2013 introduces, for the first time, the use of third-party
components with known vulnerabilities. Dependency-Track aims to document the
usage of all components, the vendors, libraires, versions and licenses used
and provide visibility into the use of vulnerable components.

Development of Dependency-Track is sponsored in part by [Axway]

More information can be found on the [wiki].


Usage
-

> $ mvn package

Finally, deploy the resulting WAR to your web application server (Tomcat, Jetty, etc)


Mailing List
-

Subscribe: [https://lists.owasp.org/mailman/listinfo/owasp_dependency_track_project] [subscribe]

Post: [owasp_dependency_track_project@lists.owasp.org] [post]

Copyright & License
-

Dependency-Track is Copyright (c) Axway. All Rights Reserved.

Permission to modify and redistribute is granted under the terms of the GPLv3
license. See the [LICENSE.txt] [GPLv3] file for the full license.

Dependency-Track makes use of several other open source libraries. Please see
the [NOTICES.txt] [notices] file for more information.


  [wiki]: https://www.owasp.org/index.php/OWASP_Dependency_Track_Project
  [subscribe]: https://lists.owasp.org/mailman/listinfo/owasp_dependency_track_project
  [post]: mailto:owasp_dependency_track_project@lists.owasp.org
  [GPLv3]: https://github.com/stevespringett/dependency-track/blob/master/LICENSE.txt
  [notices]: https://github.com/stevespringett/dependency-track/blob/master/NOTICES.txt
  [axway]: http://www.axway.com
