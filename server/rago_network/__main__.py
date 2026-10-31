"""python -m rago_network [--config config.json]          runs the server
python -m rago_network --config config.json role <uuid> <role>   sets a player's role"""

import argparse
import json
import os

from .app import ROLES, Network, dashed, serve, undashed


def main():
    parser = argparse.ArgumentParser(prog="rago_network")
    parser.add_argument("--config", default=os.environ.get("RAGO_NETWORK_CONFIG", "config.json"))
    parser.add_argument("command", nargs="*")
    args = parser.parse_args()
    with open(args.config, encoding="utf-8") as f:
        config = json.load(f)
    network = Network(config)
    if args.command[:1] == ["role"]:
        if len(args.command) != 3 or undashed(args.command[1]) is None or args.command[2] not in ROLES:
            parser.error("role <uuid> <" + "|".join(ROLES) + ">")
        changed = network.store.set_role(dashed(undashed(args.command[1])), args.command[2])
        print("updated" if changed else "no such player (they have to sign in once first)")
        return
    server = serve(network, config.get("host", "0.0.0.0"), int(config.get("port", 8080)),
                   bool(config.get("trust_proxy", False)))
    print(f"Rago Network on {server.server_address[0]}:{server.server_address[1]}")
    server.serve_forever()


if __name__ == "__main__":
    main()
