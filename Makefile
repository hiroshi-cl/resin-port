PREFIX=/Users/ferg/ws/resin

all	: 
	(cd modules/c/src; $(MAKE))

clean	:
	(cd modules/c/src; $(MAKE) clean)

install	:
	(cd modules/c/src; $(MAKE) install)
	if test $(PREFIX) != `pwd`; then \
	  mkdir -p $(PREFIX)/lib; \
	  mkdir -p $(PREFIX)/libexec; \
	  cp -r libexec/* $(PREFIX)/libexec; \
	  cp lib/*.jar $(PREFIX)/lib; \
	  mkdir -p $(PREFIX)/bin; \
	  cp bin/* $(PREFIX)/bin; \
	  mkdir -p $(PREFIX)/webapps; \
	  cp -r webapps/* $(PREFIX)/webapps; \
	  mkdir -p $(PREFIX)/conf; \
	  cp conf/resin.conf $(PREFIX)/conf/resin.conf.orig; \
	  cp conf/app-default.xml $(PREFIX)/conf/app-default.xml.orig; \
	  if test ! -r $(PREFIX)/conf/resin.conf; then \
	    cp conf/resin.conf $(PREFIX)/conf/resin.conf; \
	    cp conf/app-default.xml $(PREFIX)/conf/app-default.xml; \
	  fi; \
	fi
