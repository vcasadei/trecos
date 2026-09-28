/opsx:explore I want to create a mobile firts application as optimized as possible to run on Android. It should run from Android 9 upwards and be a native Android App. This app is named Cubby and 
  it is a offline-first Android app for home inventory tracking. I want to record what I own, where it's physically stored (e.g. "garage shelf B"), quantity, and last changed date. I also want to 
  generate simple QRcodes for each item and each storage place, so I could print those qrcodes and search the database, or the qrcode could be created by another program and I can just import it. I 
  believe that the qrcode could be simply the name of the item or the name of the storage place. I want a hierarchy of places, so I could create a Home, inside the home I could have Bedroom and 
  Office, in Office I could have various storage boxes and inside those boxes I could have my items. I want it to also be able to take pictures of both items and storage places and these pictures be
  shown on the interface as in a small preview and a bigger preview if oppened. The storage places could be called Containers. The QRcode could be the actual name, so an item or container could be 
  named "Armario#1" and the qrcode would translate that directly. Each item should have a category and the app should have a list of default categories that should be ready to use, such as 
  electronics, SBCs, computers, laptops, mice, etc. But the user could also add custom categories. Each category should have a representative icon (with a default empty one if not set). Each item 
  should have a description, a quantity, an estimated price. A future, future feature could include to create searcheable detail fields based on the description, so if I register a raspi 3b+ with 
  1gb it should suggest SBC cattegory, RAM with 1GB, Brand Raspberry Pi etc, but this is a future nice to have. Every name and description should be searchable, but the user can choose to search for
  name or description or both (both is default). The user can search for items and containers (default) or choose for one of them. Another nice to have is to be able to take a picture of the item 
  and try to find it in the database. Another nice to have is to try and read text from the item packaging and try to prefill name and description and categories and tags with that information. When
  adding a new item, it should be possible to add multiple pictures, and select the main one (by default it should be the first). When adding an item or container, I should add it completely 
  manually or I could scan the QRCode and it prefill the qrcode and try to fill the name. When seeing an item or subcategory or sub-container, I should see the full path of hierarchy and be able to 
  move up by clicking the level I want by the name (it should be separated by ">") Optional fields for items are Brand, Model, SerialNumber, QRcode, Number of items(default 1), price per item, tags,
  description, picture, the required fiels should be only Name. I should be able to move, delete, copy or duplicate items. Each container should be required to have a name (description, qrcode and 
  pictures are optional), also an optional field that can be autocalculated or overritten by the user is value of container (with the sum of item values). The user should be able to choose the 
  language, if they use metric, what currency items should be displayed. The user should be able to configure and add new fields on the settings (that applies for every item) or on a by item basis. 
  The user should be able to choose in the settings if the process for adding a new item begins with the camera or form filling, The user should be able to choose the default image source as camera 
  (official phones camera app, gallery or ask every time), the default is to ask every time, but it should ask the user for their preference on first use, and allow it to be changed on the settings.
  I should be able to "register" myself, by creating a profile with name, email (login id) and password. The user should be able to choose and edit in setting if login is required fo accessing the 
  app. At the beggiging we will only allow one user, multiple user is a nice to have but lowest priority. We should be able to use android biometrics for easy login, saving the users email as 
  default. I should be able to search for items on any page by clicking a search icon. I also should be able to add an item on any container by a "plus" floating icon on the bottom right of the 
  screen. The default listing is [Picture] Name of Item/Container for condensed. But we also could have a detailed view with a bigger image, Name of item/container, in smaller font, description, 
  quantity of items in container or quantity of the same item that I have, price per item (And I should be able to configure on settings which other default fields I want to show on the detailed 
  listing). When I enter a container, it should firstly list subcontainers in with a "Containers" title and below that, items with an "Items" title. When I enter a container, it should list the 
  subontainers/items, but the top bar should show a edit icon (alogside with the search one), a three dot option to move, delete or copy, on the top left we have the name of the container, below the
  top bar we have the image carrousel with the main image in focus, in landscape, below that it should have a Location and show the clickable path to that container, the qrcode code and them list 
  containers and items. Also whenever possible, each screen should have a return icon at the top left <-. The search funcion should bring all items with a count, they could be filtered by category, 
  tags, they could be listed by name alphabetical (detault) or by date added, of by price. I should be able to choose to show only items or only ocntainers or both in the search, the default is only
  items. I should be able to search by qrcode scan. The qrcodescan shold be done by google using google play services if possible. I shuld be able to delete a container and item by clicking the 
  three dots and selecting delete. If it's a container, I should choose another container to hold all items, create a new container to hold items in the same hierarchy or move to another parent 
  contiainer in the house, Or simply delete all items in the container. I should be able to list items by rooms/containers or items as the default home screen. It should have a bottom navigation bar
  with 4 options: Home (with rooms and the option to add new rooms and inside each room add containers and items and so forth), Items (item list with search option), Settings, do you recommend 
  another menu item to be forth or it only needs to be 3? All data should be local and internet access should never be required to use the app, however, the user may choose a Google Drive and 
  connect to a google account to store their database and sync. The user should be able to set this up and edit the google drive sync on settings, the user should also be able to edit the frequency 
  of sync between (whenever the app is opened and internet is available, every 1 day, every 5 days, every 15 days, every 30 days, never), the user should be able to manually sync using a button. The
  sync and backup process should be similar to GIT, so we could use a sort of text file, such as a pico or something like that, you may suggest the format. When syncing and there are conflicts, 
  there should be an interface for the user to select what should be merged and how, but only if there are conflicts, thats the git similarity. Each sync should have a timestamp, user and location 
  if available, also the device id. The settings should have a small FAQ with 6 to 10 basic questions max on how to use or do things in the app in an expandable accordion. The settings should also 
  have a rate this app button that goes to the play store rating form. The app should have a dark (optimized for oled screens) white and offwhite skins. The design should be minimalistic and simple,
  but sophisticated and it should perform well on even older phones, so it should be lightweight. After all the main features (not counting the nice to haves), we should have a buy me a coffee link
  and if there is a google automated option for donation, we should add as well. The app will always be free and open source in github. The license should allow anyone to use and build on top of it
  with a reference to me, but it should now allow for creating products that charge anything from anyone. If someone wants to create a product that charges people, they should contact me and get a 
  quote for licensing. I don't have a way of earning money in this app, and I don't want ads ever. Do you have any suggestion on how we could earn money? The interface should feel modern and snappy 
  with minimal but fast and easy to render animations. I believe that's it. With all this information can you help me refine all requisites and features and create proposals for this app? Also, the 
  name is Cubby - Offline First Home Inventory, do you have a better idea?
Finally I want you to structure the repository to have a comprehensive documentation.
For you to know, I'm running this session on a remote headless x86 machine with linux ubuntu.

