# APStatus
Monitor your device remotely while it's in access point mode

This program allows you to forward information about your device's power and mobile data connection state to another device over the network. On newer platforms it also may to forward notifications from any of your apps.
The primary goal of APStatus is to inform you about the state of the phone while it is using as an access point. You can check signal and battery levels remotely. APStatus lets you know about incoming call or message.
Actually you can configure the program to monitor any android device through the network.

[Become a beta tester](https://play.google.com/apps/testing/pnapp.tools.apstatus)

## Gson (2016.07.05)
The try to replace direct serialization/deserialization of Intent-object by the Gson is now canceled because impossible to determine exact type of object at deserialization. For example serialized byte array transforms into ArrayList of Integers and so I need to extract it manualy on the high level.
