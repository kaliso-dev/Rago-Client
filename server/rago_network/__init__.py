"""Rago Network: which players use Rago Client, their role and their cosmetics.

Players prove who they are with Mojang's session server (the same check a Minecraft server
makes when you join it): the client asks Mojang to note that it is joining a random "server id",
and this server asks Mojang whether that player did. The player's Minecraft token goes to Mojang
only, never here.
"""

__version__ = "1.0.0"
