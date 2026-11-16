
package com.example.examplemod;

import com.example.examplemod.api.DeathLootCharges;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = ExampleMod.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public class DeathLootCommands {

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {

        event.getDispatcher().register(
                Commands.literal("deathloot")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("chance")
                                .then(Commands.argument(
                                                "numero",
                                                IntegerArgumentType.integer(0, 100)
                                        )
                                        .executes(context -> {
                                            int chance =
                                                    IntegerArgumentType.getInteger(
                                                            context, "numero"
                                                    );

                                            Config.setDeathLootChance(chance);

                                            context.getSource().sendSuccess(
                                                    () -> Component.literal(
                                                            "Probabilidad de pérdida de objetos: "
                                                                    + chance + "%"
                                                    ),
                                                    true
                                            );

                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("charges")
                                .then(Commands.literal("tp")
                                        .then(Commands.argument("jugador", EntityArgument.player())
                                                .then(Commands.argument("cantidad", IntegerArgumentType.integer(0, 999))
                                                        .executes(context -> {
                                                            ServerPlayer target = EntityArgument.getPlayer(context, "jugador");
                                                            int amount = IntegerArgumentType.getInteger(context, "cantidad");
                                                            DeathLootCharges.setFreeTeleports(target, amount);
                                                            context.getSource().sendSuccess(() -> Component.literal(
                                                                    target.getName().getString() + ": "
                                                                            + DeathLootCharges.freeTeleports(target)
                                                                            + " tp gratis"), true);
                                                            return 1;
                                                        }))))
                                .then(Commands.literal("time")
                                        .then(Commands.argument("jugador", EntityArgument.player())
                                                .then(Commands.argument("minutos", IntegerArgumentType.integer(1, 1440))
                                                        .then(Commands.argument("cantidad", IntegerArgumentType.integer(0, 999))
                                                                .executes(context -> {
                                                                    ServerPlayer target = EntityArgument.getPlayer(context, "jugador");
                                                                    int minutes = IntegerArgumentType.getInteger(context, "minutos");
                                                                    int amount = IntegerArgumentType.getInteger(context, "cantidad");
                                                                    DeathLootCharges.setFreeExtensions(target, minutes, amount);
                                                                    context.getSource().sendSuccess(() -> Component.literal(
                                                                            target.getName().getString() + ": " + amount
                                                                                    + " carga(s) gratis de " + minutes + "m"), true);
                                                                    return 1;
                                                                })))))
                                .then(Commands.literal("check")
                                        .then(Commands.argument("jugador", EntityArgument.player())
                                                .executes(context -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(context, "jugador");
                                                    context.getSource().sendSuccess(() -> Component.literal(
                                                            target.getName().getString()
                                                                    + " -> tp: " + DeathLootCharges.freeTeleports(target)
                                                                    + " | tiempo: "
                                                                    + DeathLootCharges.allFreeExtensions(target)), false);
                                                    return 1;
                                                }))))
        );
    }
}
