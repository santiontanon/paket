# PAKET (Point And Klick Engine Tool) Compiler v1.0 alpha

PAKET is a tool designed to create point and click adventure games for 8 bit platforms. PAKET was developed by Santiago Ontañón in 2019, in collaboration with Jordi Sureda, in order to create a series of point and click games for 8 bit platforms. It currently supports generating games for Amstrad CPC 464 and for MSX computers. Any questions about the engine can be addressed to us via email or social media (we should be easy to find).  
  
PAKET consists of a game definition language, with which all the objects, rooms and scripts of the game are defined, and a compiler that generates the game binary given the game definition.  

*   Game definition language (.pak)]
*   PAKET Compiler

The main features of PAKET are:

*   Cross-platform compiler (Java-based) to generate games given .pak definitions.
*   The same .pak game definition can be compiled to different platforms (CPC464 and MSX), given appropriate graphic files.
*   Localization files can be added to add different languages to the game (e.g., Spanish, English, etc.).
*   The resulting games are played in the style of classic Lucasarts games, with 4 main verbs (examine, pick-up, use and talk), auxiliary verbs that are available contextually (exit), and an inventory system.
*   The game is controlled using a cursor-key controlled pointer plys space, plus a few short-cuts:
    *   Arrow keys: move the cursor.
    *   Space: click.
    *   Shift: slow the speed of the cursor, for better pixel hunting, if needed.
    *   TAB: cycles throught he verbs.
    *   I/J: automatically cycles the pointer through the iterms in the inventory.
    *   M: mute/unmute music (in Amstrad CPC).
    *   F1: mute/unmute music (in MSX).
*   The engine support savegames to cassette tape and disk in Amstrad CPC, and using a password system in MSX.

The PAKET engine is very limited, but this was enough for us to develop all threee episodes of The Key. For example, it does not support things like controlling multiple characters, or many other advanced features you would expect in classic point-and-click adventure games like Maniac Mansion.
  
An example screenshot of a game developed with PAKET is shown in the following screenshots (running on an Amstrad CPC 464, graphics by Jordi Sureda):

* * *

# Outline

*   [Overview](https://github.com/santiontanon/paket/wiki/Documentation-%E2%80%90-Overview)
*   Supported Platforms
*   Game Definition Language (.pak)
    *   Comments
    *   Global Text Definitions
    *   Screen Dimensions Definitions
    *   Other Global Definitions
    *   Localization Files
    *   Item Definition
    *   Object Type Definition
    *   Room Definition
    *   Scripts
    *   Dialogue Definitions
    *   Cutscene Definitions
    *   Saving and Loading Games
    *   Sound Effects
    *   Music
*   PAKET Compiler

* * *

# Tutorials

* Tutorial 1: Hello World
* Tutorial 2: Objects
* Tutorial 3: Player
* Tutorial 4: Multiple Rooms
* Tutorial 5: Bells and Whistles

* * *

# Example Full Games

See the "examples" folder for the full source code and media files for all three episodes of "The Key", and also our initial demo "Escape the Rom".

