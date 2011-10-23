Picasa Plugin for MultiPicture Live Wallpaper
=============================================

Introduction
------------

[Plugin for MultiPicture Live Wallpaper]
(http://www.tamanegi.org/prog/android-apps/mplwp-plugins.html#mplwp-picasa)
show pictures from [Picasa Web Album](http://picasaweb.google.com/).  
This plugin needs [MultiPicture Live Wallpaper]
(http://www.tamanegi.org/prog/android-apps/mplwp.html) v0.5.1 or later.


Build
-----

Prerequisites:

* [Android SDK](http://developer.android.com/sdk/index.html)
* [Eclipse](http://www.eclipse.org/downloads/)
* [ADT Plugin for Eclipse](http://developer.android.com/sdk/eclipse-adt.html)

Library:

* Download google-api-java-client-1.5.0-beta.zip from
  [google-api-java-client](http://code.google.com/p/google-api-java-client/)
* Copy following files from zip file to libs/ directory.
  - google-api-client-1.5.0-beta.jar
  - google-http-client-1.5.0-beta.jar
  - google-http-client-extensions-android2-1.5.0-beta.jar
  - guava-r09.jar

Import to Eclipse:

* File > Import... > Existing Project into Workspace
* Choose this directory and Finish

Then clean, compile and run.
