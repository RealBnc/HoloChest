package com.holochest.hologram;

import com.holochest.render.HoloHudRenderer;
import com.holochest.sound.HoloSoundHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class HoloChestManager {

    private static final HoloChestManager INSTANCE = new HoloChestManager();

    private final Map<BlockPos, HoloSession> activeSessions = new ConcurrentHashMap<>();

    private HoloSession hoveredSession = null;
    private int hoveredSlot = -1;
    private int lastHoveredSlot = -1;
    private BlockPos lastHoveredSessionPos = null;

    private boolean isAimingAtPanel = false;

    private final List<FlyingItem> flyingItems = new CopyOnWriteArrayList<>();

    private HoloChestManager() {}

    public static HoloChestManager getInstance() {
        return INSTANCE;
    }

    public boolean hasActiveSessions() {
        return !activeSessions.isEmpty();
    }

    public boolean hasAnyNonClosingSession() {
        for (HoloSession session : activeSessions.values()) {
            if (!session.isClosing()) {
                return true;
            }
        }
        return false;
    }

    public Collection<HoloSession> getActiveSessions() {
        return activeSessions.values();
    }

    public HoloSession getSession(BlockPos pos) {
        return activeSessions.get(pos);
    }

    public List<FlyingItem> getFlyingItems() {
        return flyingItems;
    }

    public HoloSession getHoveredSession() {
        return hoveredSession;
    }

    public int getHoveredSlot() {
        return hoveredSlot;
    }

    public boolean isAimingAtAnyPanel() {
        return isAimingAtPanel;
    }

    public void setAimingAtPanel(boolean aiming) {
        this.isAimingAtPanel = aiming;
    }

    public void setHoverState(HoloSession session, int slot) {
        this.hoveredSession = session;
        this.hoveredSlot = slot;

        BlockPos currentPos = (session != null) ? session.getPos() : null;
        boolean posChanged = (currentPos != null && !currentPos.equals(lastHoveredSessionPos)) || (currentPos == null && lastHoveredSessionPos != null);

        if (slot != this.lastHoveredSlot || posChanged) {
            if (slot != -1) {
                HoloSoundHelper.playSlotHover();
            }
            this.lastHoveredSlot = slot;
            this.lastHoveredSessionPos = currentPos;
        }
    }

    public boolean isContainerAjar(BlockPos pos) {
        if (pos == null || activeSessions.isEmpty()) return false;
        for (HoloSession session : activeSessions.values()) {
            if (session.isMatchingPos(pos)) {
                return true;
            }
        }
        return false;
    }

    public float getContainerOpenProgress(BlockPos pos) {
        if (pos == null) return 0f;
        for (HoloSession session : activeSessions.values()) {
            if (session.isMatchingPos(pos)) {
                return session.getOpenProgress();
            }
        }
        return 0f;
    }

    public void toggle() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return;

        net.minecraft.world.phys.BlockHitResult hit = raycastContainer(player, client);
        if (hit != null && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            double distSq = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distSq <= 40.0 * 40.0) {
                BlockState state = client.level.getBlockState(pos);

                if (isContainerBlock(state)) {
                BlockPos canonPos = getCanonicalChestPos(client.level, pos, state);
                HoloSession current = activeSessions.get(canonPos);
                if (current != null && !current.isClosing()) {
                    float curProg = current.getOpenProgress();
                    startClosingSession(current);
                    HoloSoundHelper.playHoloClose(curProg);
                } else {
                    openContainer(canonPos, state);
                }
                    return;
                }
            }
        }

        if (!activeSessions.isEmpty()) {
            startClosingAll();
        }
    }

    public static boolean isContainerBlock(BlockState state) {
        Block b = state.getBlock();
        return b instanceof ChestBlock
                || b instanceof BarrelBlock
                || b instanceof ShulkerBoxBlock
                || b instanceof HopperBlock
                || b instanceof AbstractFurnaceBlock
                || b instanceof DispenserBlock
                || b instanceof DropperBlock
                || b instanceof CrafterBlock
                || b instanceof BrewingStandBlock
                || b instanceof ChiseledBookShelfBlock
                || b instanceof EnderChestBlock;
    }

    private BlockHitResult raycastContainer(LocalPlayer player, Minecraft client) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 rot = player.getViewVector(1.0f);
        Vec3 reachEnd = eyePos.add(rot.scale(40.0));

        return client.level.clip(new ClipContext(
                eyePos,
                reachEnd,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));
    }

    public static BlockPos getDoubleChestPartner(Level world, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(BlockStateProperties.CHEST_TYPE) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            ChestType type = state.getValue(BlockStateProperties.CHEST_TYPE);
            if (type != ChestType.SINGLE) {
                Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                Direction partnerDir = (type == ChestType.LEFT) ? facing.getClockWise() : facing.getCounterClockWise();
                BlockPos partnerPos = pos.relative(partnerDir);
                BlockState partnerState = world.getBlockState(partnerPos);
                if (partnerState.getBlock() == state.getBlock() && partnerState.hasProperty(BlockStateProperties.CHEST_TYPE)) {
                    ChestType partnerType = partnerState.getValue(BlockStateProperties.CHEST_TYPE);
                    if (partnerType != ChestType.SINGLE && partnerType != type && partnerState.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing) {
                        return partnerPos;
                    }
                }
            }
        }
        return null;
    }

    public static BlockPos getCanonicalChestPos(Level world, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(BlockStateProperties.CHEST_TYPE) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            ChestType type = state.getValue(BlockStateProperties.CHEST_TYPE);
            if (type == ChestType.RIGHT) {
                Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                Direction partnerDir = facing.getCounterClockWise();
                BlockPos partnerPos = pos.relative(partnerDir);
                BlockState partnerState = world.getBlockState(partnerPos);
                if (partnerState.getBlock() == state.getBlock() && partnerState.hasProperty(BlockStateProperties.CHEST_TYPE)) {
                    if (partnerState.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.LEFT && partnerState.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing) {
                        return partnerPos;
                    }
                }
            }
        }
        return pos;
    }

    public static SoundEvent getContainerOpenSound(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof ShulkerBoxBlock) return SoundEvents.SHULKER_BOX_OPEN;
        if (b instanceof BarrelBlock) return SoundEvents.BARREL_OPEN;
        if (b instanceof EnderChestBlock) return SoundEvents.ENDER_CHEST_OPEN;
        if (b instanceof HopperBlock || b instanceof DispenserBlock || b instanceof DropperBlock || b instanceof CrafterBlock) {
            return SoundEvents.IRON_TRAPDOOR_OPEN;
        }
        if (b instanceof AbstractFurnaceBlock) return SoundEvents.FURNACE_FIRE_CRACKLE;
        return SoundEvents.CHEST_OPEN;
    }

    public static SoundEvent getContainerCloseSound(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof ShulkerBoxBlock) return SoundEvents.SHULKER_BOX_CLOSE;
        if (b instanceof BarrelBlock) return SoundEvents.BARREL_CLOSE;
        if (b instanceof EnderChestBlock) return SoundEvents.ENDER_CHEST_CLOSE;
        if (b instanceof HopperBlock || b instanceof DispenserBlock || b instanceof DropperBlock || b instanceof CrafterBlock) {
            return SoundEvents.IRON_TRAPDOOR_CLOSE;
        }
        if (b instanceof AbstractFurnaceBlock) return SoundEvents.IRON_TRAPDOOR_CLOSE;
        return SoundEvents.CHEST_CLOSE;
    }

    public static int getContainerTitleColor(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof ShulkerBoxBlock shulker) {
            DyeColor dye = shulker.getColor();
            if (dye != null) {
                return switch (dye) {
                    case WHITE -> 0xF9FFFE;
                    case ORANGE -> 0xF9801D;
                    case MAGENTA -> 0xC74EBD;
                    case LIGHT_BLUE -> 0x3AB3DA;
                    case YELLOW -> 0xFED83D;
                    case LIME -> 0x80C71F;
                    case PINK -> 0xF38BAA;
                    case GRAY -> 0x474F52;
                    case LIGHT_GRAY -> 0x9D9D97;
                    case CYAN -> 0x169C9C;
                    case PURPLE -> 0x9933FF;
                    case BLUE -> 0x3C44AA;
                    case BROWN -> 0x835432;
                    case GREEN -> 0x5E7C16;
                    case RED -> 0xEF4444;
                    case BLACK -> 0x303030;
                };
            }
            return 0xBA68C8;
        }
        if (b instanceof EnderChestBlock) {
            return 0x20E3B2;
        }
        if (b instanceof BarrelBlock) {
            return 0xD29034;
        }
        if (b instanceof ChestBlock) {
            return 0xF0A852;
        }
        if (b instanceof HopperBlock) {
            return 0xC0C0D4;
        }
        if (b instanceof AbstractFurnaceBlock) {
            return 0xFF7700;
        }
        if (b instanceof BrewingStandBlock) {
            return 0xF59E0B;
        }
        if (b instanceof DispenserBlock || b instanceof DropperBlock || b instanceof CrafterBlock) {
            return 0x9CA3AF;
        }
        return 0x00E5FF;
    }

    public void openContainer(BlockPos pos, BlockState state) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        BlockPos canonPos = getCanonicalChestPos(client.level, pos, state);

        for (HoloSession s : activeSessions.values()) {
            startClosingSession(s);
        }

        BlockState canonState = client.level.getBlockState(canonPos);
        BlockPos partnerPos = getDoubleChestPartner(client.level, canonPos, canonState);

        Component title = canonState.getBlock().getName();
        int titleColor = getContainerTitleColor(canonState);
        SoundEvent openSound = getContainerOpenSound(canonState);
        SoundEvent closeSound = getContainerCloseSound(canonState);

        int initialSize = 27;
        Block b = canonState.getBlock();
        if (b instanceof ChestBlock && canonState.hasProperty(BlockStateProperties.CHEST_TYPE)) {
            ChestType type = canonState.getValue(BlockStateProperties.CHEST_TYPE);
            initialSize = (type != ChestType.SINGLE) ? 54 : 27;
        } else if (b instanceof HopperBlock) {
            initialSize = 5;
        } else if (b instanceof AbstractFurnaceBlock) {
            initialSize = 3;
        } else if (b instanceof DispenserBlock || b instanceof DropperBlock || b instanceof CrafterBlock) {
            initialSize = 9;
        } else if (b instanceof BrewingStandBlock) {
            initialSize = 5;
        } else if (b instanceof ChiseledBookShelfBlock) {
            initialSize = 6;
        }

        HoloSession session = new HoloSession(canonPos, partnerPos, title, titleColor, initialSize, openSound, closeSound);
        if (b instanceof AbstractFurnaceBlock) {
            session.setFurnace(true);
        }
        activeSessions.put(canonPos, session);

        if (b instanceof EnderChestBlock && client.player != null) {
            Container clientEnder = client.player.getEnderChestInventory();
            for (int i = 0; i < clientEnder.getContainerSize() && i < session.getCachedStacks().size(); i++) {
                session.setStack(i, clientEnder.getItem(i).copy());
            }
        }

        if (b instanceof BarrelBlock && state.hasProperty(BlockStateProperties.OPEN)) {
            client.level.setBlock(canonPos, state.setValue(BlockStateProperties.OPEN, true), 3);
            MinecraftServer server = client.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    ServerLevel sWorld = server.getLevel(client.level.dimension());
                    if (sWorld != null) {
                        BlockState sState = sWorld.getBlockState(canonPos);
                        if (sState.getBlock() instanceof BarrelBlock && sState.hasProperty(BlockStateProperties.OPEN)) {
                            sWorld.setBlock(canonPos, sState.setValue(BlockStateProperties.OPEN, true), 3);
                        }
                    }
                });
            }
        }

        syncSessionFromServer(session);
        HoloSoundHelper.playHoloOpen(0f);
    }

    

    

    public void recalculateSessionLayout() {}

    public void syncSessionFromServer(HoloSession session) {
        Minecraft client = Minecraft.getInstance();
        MinecraftServer server = client.getSingleplayerServer();

        if (server != null && client.level != null && client.player != null) {
            BlockPos targetPos = session.getPos();
            java.util.UUID playerUuid = client.player.getUUID();

            server.execute(() -> {
                ServerLevel sWorld = server.getLevel(client.level.dimension());
                if (sWorld == null) return;

                ServerPlayer sPlayer = server.getPlayerList().getPlayer(playerUuid);
                Container inv = getContainerInventoryDirect(sWorld, targetPos, sPlayer);
                if (inv != null) {
                    int size = inv.getContainerSize();
                    NonNullList<ItemStack> copy = NonNullList.withSize(size, ItemStack.EMPTY);
                    for (int i = 0; i < size; i++) {
                        copy.set(i, inv.getItem(i).copy());
                    }

                    float litProg = 0f;
                    float cookProg = 0f;
                    BlockEntity be = sWorld.getBlockEntity(targetPos);
                    if (be instanceof AbstractFurnaceBlockEntity furnace) {
                        try {
                            net.minecraft.world.inventory.ContainerData pd = ((com.holochest.mixin.AbstractFurnaceBlockEntityAccessor) furnace).getDataAccess();
                            if (pd != null) {
                                int litRemaining = pd.get(0);
                                int litTotal = pd.get(1);
                                int cookSpent = pd.get(2);
                                int cookTotal = pd.get(3);
                                litProg = (litTotal > 0) ? Math.clamp((float) litRemaining / (float) litTotal, 0f, 1f) : 0f;
                                cookProg = (cookTotal > 0) ? Math.clamp((float) cookSpent / (float) cookTotal, 0f, 1f) : 0f;
                            }
                        } catch (Throwable ignored) {}
                    }
                    final float finalLitProg = litProg;
                    final float finalCookProg = cookProg;

                    client.execute(() -> {
                        if (activeSessions.containsKey(targetPos)) {
                            session.updateSize(size);
                            for (int i = 0; i < size && i < session.getCachedStacks().size(); i++) {
                                session.setStack(i, copy.get(i));
                            }
                            session.setFurnaceLitProgress(finalLitProg);
                            session.setFurnaceCookProgress(finalCookProg);
                        }
                    });
                }
            });
        }
    }

    public static Container getContainerInventoryDirect(ServerLevel sWorld, BlockPos pos, ServerPlayer sPlayer) {
        BlockState state = sWorld.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof ChestBlock chestBlock) {
            return ChestBlock.getContainer(chestBlock, state, sWorld, pos, true);
        }
        if (block instanceof EnderChestBlock && sPlayer != null) {
            return sPlayer.getEnderChestInventory();
        }

        BlockEntity be = sWorld.getBlockEntity(pos);
        if (be instanceof Container inv) {
            return inv;
        }
        return null;
    }

    public void startClosingAll() {
        float maxProg = 0f;
        boolean anyNotClosing = false;
        for (HoloSession session : activeSessions.values()) {
            if (!session.isClosing()) {
                maxProg = Math.max(maxProg, session.getOpenProgress());
                anyNotClosing = true;
            }
            startClosingSession(session);
        }
        if (anyNotClosing) {
            HoloSoundHelper.playHoloClose(maxProg > 0f ? maxProg : 1.0f);
        }
        
    }

    private BlockPos lastInteractedPos = null;
    private long lastInteractionTime = 0;

    public void recordBlockInteraction(BlockPos pos) {
        this.lastInteractedPos = pos;
        this.lastInteractionTime = System.currentTimeMillis();
    }

    public BlockPos getLastInteractedBlockPos() {
        return lastInteractedPos;
    }

    public void onVanillaContainerScreenOpened() {
        Minecraft client = Minecraft.getInstance();
        BlockPos targetPos = lastInteractedPos;
        long elapsed = System.currentTimeMillis() - lastInteractionTime;

        if (targetPos == null || elapsed > 3000) {
            if (client.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
                targetPos = bhr.getBlockPos();
            }
        }

        if (targetPos == null && client.level != null && client.player != null) {
            Vec3 playerPos = client.player.getEyePosition();
            double closestDistSq = Double.MAX_VALUE;
            BlockPos closestPos = null;
            for (HoloSession session : activeSessions.values()) {
                double distSq = session.getPos().distToCenterSqr(playerPos);
                if (distSq < closestDistSq && distSq <= 36.0) {
                    closestDistSq = distSq;
                    closestPos = session.getPos();
                }
            }
            targetPos = closestPos;
        }

        if (targetPos == null) return;

        BlockPos toRemovePos = null;
        for (HoloSession session : activeSessions.values()) {
            if (session.isMatchingPos(targetPos)) {
                toRemovePos = session.getPos();
                break;
            }
        }

        if (toRemovePos != null) {
            HoloSession s = activeSessions.get(toRemovePos);
            if (s != null && !s.isClosing()) {
                float curProg = s.getOpenProgress();
                startClosingSession(s);
                HoloSoundHelper.playHoloClose(curProg);
            }
        }

        lastInteractedPos = null;
    }

    public void forceCloseAll() {
        flyingItems.clear();
        closeBarrelBlocks();
        activeSessions.clear();
        hoveredSession = null;
        hoveredSlot = -1;
        lastHoveredSlot = -1;
        lastHoveredSessionPos = null;
        isAimingAtPanel = false;
    }

    public void startClosingSession(HoloSession session) {
        if (session == null) return;
        if (!session.isClosing()) {
            session.startClosing();
        }
        closeBarrelBlock(session.getPos());
        if (session.getPartnerPos() != null) {
            closeBarrelBlock(session.getPartnerPos());
        }
    }

    public void closeBarrelBlock(BlockPos pos) {
        if (pos == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        BlockState state = client.level.getBlockState(pos);
        if (state.getBlock() instanceof BarrelBlock && state.hasProperty(BlockStateProperties.OPEN)) {
            client.level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, false), 3);
        }
        MinecraftServer server = client.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                ServerLevel sWorld = server.getLevel(client.level.dimension());
                if (sWorld != null) {
                    BlockState sState = sWorld.getBlockState(pos);
                    if (sState.getBlock() instanceof BarrelBlock && sState.hasProperty(BlockStateProperties.OPEN)) {
                        sWorld.setBlock(pos, sState.setValue(BlockStateProperties.OPEN, false), 3);
                    }
                }
            });
        }
    }

    private void closeBarrelBlocks() {
        for (HoloSession session : activeSessions.values()) {
            closeBarrelBlock(session.getPos());
            if (session.getPartnerPos() != null) {
                closeBarrelBlock(session.getPartnerPos());
            }
        }
    }

    public static boolean isShiftHeld() {
        Minecraft client = Minecraft.getInstance();
        return client.options != null && client.options.keyShift.isDown();
    }

    public static Vector2f getHotbarSlotScreenPos(int hotbarSlot, int sw, int sh) {
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            return new Vector2f(sw * 0.65f, sh - 20f);
        }
        float hotbarStartX = (sw - 182) / 2f;
        float slotCenterX = hotbarStartX + 3f + (hotbarSlot * 20f) + 8f;
        float slotCenterY = sh - 11f;
        return new Vector2f(slotCenterX, slotCenterY);
    }

    public void handleLeftClick() {
        if (hoveredSession == null) return;

        if (hoveredSession.isSortButtonHovered()) {
            sortSession(hoveredSession);
            return;
        }

        if (hoveredSlot == -1) return;

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        if (hoveredSlot >= hoveredSession.getCachedSize()) return;
        ItemStack stack = hoveredSession.getStack(hoveredSlot);
        if (stack.isEmpty()) return;

        boolean shiftHeld = isShiftHeld();
        int countToTake = shiftHeld ? stack.getCount() : 1;

        ItemStack toGive = stack.split(countToTake);
        if (stack.isEmpty()) {
            hoveredSession.setStack(hoveredSlot, ItemStack.EMPTY);
        }

        if (client.level != null) {
            BlockState bState = client.level.getBlockState(hoveredSession.getPos());
            if (bState.getBlock() instanceof EnderChestBlock) {
                player.getEnderChestInventory().setItem(hoveredSlot, hoveredSession.getStack(hoveredSlot).copy());
            }
        }

        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();
        Vector2f slotPos = hoveredSession.getSlotScreenPos(hoveredSlot);

        int[] countsBefore = new int[36];
        for (int i = 0; i < 36; i++) {
            countsBefore[i] = player.getInventory().getItem(i).getCount();
        }

        player.getInventory().add(toGive.copy());

        int selectedHotbar = player.getInventory().getSelectedSlot();
        int increasedSlot = -1;

        if (player.getInventory().getItem(selectedHotbar).getCount() > countsBefore[selectedHotbar]) {
            increasedSlot = selectedHotbar;
        } else {
            for (int i = 0; i < 9; i++) {
                if (player.getInventory().getItem(i).getCount() > countsBefore[i]) {
                    increasedSlot = i;
                    break;
                }
            }
        }

        Vector2f destPos = (increasedSlot >= 0 && increasedSlot < 9)
                ? getHotbarSlotScreenPos(increasedSlot, sw, sh)
                : new Vector2f(sw * 0.65f, sh - 20f);

        flyingItems.add(new FlyingItem(toGive.copy(), slotPos, destPos, 350, true));
        HoloSoundHelper.playSlotHover();

        MinecraftServer server = client.getSingleplayerServer();
        if (server != null) {
            BlockPos targetPos = hoveredSession.getPos();
            int targetSlot = this.hoveredSlot;
            UUID playerUuid = player.getUUID();

            server.execute(() -> {
                ServerPlayer sPlayer = server.getPlayerList().getPlayer(playerUuid);
                ServerLevel sWorld = server.getLevel(client.level.dimension());
                if (sPlayer == null || sWorld == null) return;

                Container sContainer = getContainerInventoryDirect(sWorld, targetPos, sPlayer);
                if (sContainer != null && targetSlot < sContainer.getContainerSize()) {
                    ItemStack realStack = sContainer.getItem(targetSlot);
                    if (!realStack.isEmpty()) {
                        int serverTakeCount = shiftHeld ? realStack.getCount() : 1;
                        ItemStack realGive = realStack.split(serverTakeCount);
                        sContainer.setChanged();

                        boolean inserted = sPlayer.getInventory().add(realGive);
                        if (!inserted) {
                            sPlayer.drop(realGive, false, net.minecraft.util.Prediction.SERVER_ONLY);
                        }
                        sPlayer.containerMenu.broadcastChanges();
                    }
                }
            });
        }
    }

    public static boolean canInsertIntoSlot(Level world, BlockState state, int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Block b = state.getBlock();
        if (b instanceof AbstractFurnaceBlock) {
            if (slot == 2) return false;
            if (slot == 1) {
                return stack.has(DataComponents.COOKING_FUEL);
            }
            return true;
        }
        return true;
    }

    public void handleRightClick() {
        if (hoveredSession == null || hoveredSlot == -1) return;

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return;

        if (hoveredSlot >= hoveredSession.getCachedSize()) return;
        ItemStack currentInSlot = hoveredSession.getStack(hoveredSlot);

        if (!currentInSlot.isEmpty()) {
            boolean isShulker = (currentInSlot.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock)
                    || currentInSlot.getItem().getDescriptionId().contains("shulker_box");

            if (isShulker) {
                hoveredSession.toggleShulkerPreview(hoveredSlot);
                HoloSoundHelper.playSlotHover();
                return;
            }
        }

        ItemStack clientHeld = player.getMainHandItem();
        if (clientHeld.isEmpty()) return;

        BlockState bState = client.level.getBlockState(hoveredSession.getPos());
        if (!canInsertIntoSlot(client.level, bState, hoveredSlot, clientHeld)) {
            return;
        }

        boolean shiftHeld = isShiftHeld();
        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();
        int selectedHotbar = player.getInventory().getSelectedSlot();
        Vector2f fromPos = getHotbarSlotScreenPos(selectedHotbar, sw, sh);
        Vector2f slotPos = hoveredSession.getSlotScreenPos(hoveredSlot);

        if (currentInSlot.isEmpty()) {
            int countToPlace = shiftHeld ? clientHeld.getCount() : 1;
            ItemStack placed = clientHeld.split(countToPlace);
            hoveredSession.setStack(hoveredSlot, placed.copy());

            if (client.level != null) {
                if (bState.getBlock() instanceof EnderChestBlock) {
                    player.getEnderChestInventory().setItem(hoveredSlot, placed.copy());
                }
            }

            flyingItems.add(new FlyingItem(placed.copy(), fromPos, slotPos, 350, false));
            HoloSoundHelper.playSlotHover();

            MinecraftServer server = client.getSingleplayerServer();
            if (server != null) {
                BlockPos targetPos = hoveredSession.getPos();
                int targetSlot = this.hoveredSlot;
                UUID playerUuid = player.getUUID();

                server.execute(() -> {
                    ServerPlayer sPlayer = server.getPlayerList().getPlayer(playerUuid);
                    ServerLevel sWorld = server.getLevel(client.level.dimension());
                    if (sPlayer == null || sWorld == null) return;

                    ItemStack held = sPlayer.getMainHandItem();
                    if (held.isEmpty()) return;

                    if (!canInsertIntoSlot(sWorld, sWorld.getBlockState(targetPos), targetSlot, held)) return;

                    Container sContainer = getContainerInventoryDirect(sWorld, targetPos, sPlayer);
                    if (sContainer != null && targetSlot < sContainer.getContainerSize()) {
                        ItemStack placedServer = held.split(countToPlace);
                        sContainer.setItem(targetSlot, placedServer);
                        sContainer.setChanged();
                        sPlayer.containerMenu.broadcastChanges();
                    }
                });
            }
        } else if (ItemStack.isSameItemSameComponents(currentInSlot, clientHeld)) {
            int max = currentInSlot.getMaxStackSize();
            int canAdd = max - currentInSlot.getCount();
            if (canAdd > 0) {
                int toAdd = shiftHeld ? Math.min(canAdd, clientHeld.getCount()) : 1;
                clientHeld.shrink(toAdd);
                currentInSlot.grow(toAdd);

                if (client.level != null) {
                    if (bState.getBlock() instanceof EnderChestBlock) {
                        player.getEnderChestInventory().setItem(hoveredSlot, currentInSlot.copy());
                    }
                }

                ItemStack anim = clientHeld.copyWithCount(toAdd);
                flyingItems.add(new FlyingItem(anim, fromPos, slotPos, 350, false));
                HoloSoundHelper.playSlotHover();

                MinecraftServer server = client.getSingleplayerServer();
                if (server != null) {
                    BlockPos targetPos = hoveredSession.getPos();
                    int targetSlot = this.hoveredSlot;
                    UUID playerUuid = player.getUUID();

                    server.execute(() -> {
                        ServerPlayer sPlayer = server.getPlayerList().getPlayer(playerUuid);
                        ServerLevel sWorld = server.getLevel(client.level.dimension());
                        if (sPlayer == null || sWorld == null) return;

                        ItemStack held = sPlayer.getMainHandItem();
                        if (held.isEmpty()) return;

                        Container sContainer = getContainerInventoryDirect(sWorld, targetPos, sPlayer);
                        if (sContainer != null && targetSlot < sContainer.getContainerSize()) {
                            ItemStack inSlot = sContainer.getItem(targetSlot);
                            held.shrink(toAdd);
                            inSlot.grow(toAdd);
                            sContainer.setChanged();
                            sPlayer.containerMenu.broadcastChanges();
                        }
                    });
                }
            }
        }
    }

    public void sortFocusedSession() {
        HoloSession session = (hoveredSession != null) ? hoveredSession : null;
        if (session == null) {
            for (HoloSession s : activeSessions.values()) {
                if (!s.isClosing()) {
                    session = s;
                    break;
                }
            }
        }
        if (session != null) {
            sortSession(session);
        }
    }

    public void sortSession(HoloSession session) {
        if (session == null || session.isFurnace()) return;

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return;

        int totalSlots = session.getCachedSize();
        if (totalSlots <= 0) return;

        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < totalSlots; i++) {
            ItemStack s = session.getStack(i);
            if (!s.isEmpty()) {
                stacks.add(s.copy());
            }
        }

        if (stacks.isEmpty()) return;

        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            for (ItemStack existing : merged) {
                if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                    int canTake = existing.getMaxStackSize() - existing.getCount();
                    int move = Math.min(canTake, stack.getCount());
                    existing.grow(move);
                    stack.shrink(move);
                    if (stack.isEmpty()) break;
                }
            }
            if (!stack.isEmpty()) {
                merged.add(stack);
            }
        }

        merged.sort((a, b) -> {
            int catA = getItemSortCategory(a);
            int catB = getItemSortCategory(b);
            if (catA != catB) {
                return Integer.compare(catA, catB);
            }

            String regA = BuiltInRegistries.ITEM.getKey(a.getItem()).getPath();
            String regB = BuiltInRegistries.ITEM.getKey(b.getItem()).getPath();

            if (catA == 0) {
                int tierA = getMaterialTier(regA);
                int tierB = getMaterialTier(regB);
                if (tierA != tierB) {
                    return Integer.compare(tierA, tierB);
                }
                int subA = getWeaponToolSubIndex(regA);
                int subB = getWeaponToolSubIndex(regB);
                if (subA != subB) {
                    return Integer.compare(subA, subB);
                }
            }

            if (catA == 1) {
                int tierA = getMaterialTier(regA);
                int tierB = getMaterialTier(regB);
                if (tierA != tierB) {
                    return Integer.compare(tierA, tierB);
                }
                int slotA = getArmorSlotIndex(regA);
                int slotB = getArmorSlotIndex(regB);
                if (slotA != slotB) {
                    return Integer.compare(slotA, slotB);
                }
            }

            if (catA == 2) {
                int minTierA = getMineralTier(regA);
                int minTierB = getMineralTier(regB);
                if (minTierA != minTierB) {
                    return Integer.compare(minTierA, minTierB);
                }
            }

            String nameA = a.getHoverName().getString();
            String nameB = b.getHoverName().getString();
            int nameComp = nameA.compareToIgnoreCase(nameB);
            if (nameComp != 0) {
                return nameComp;
            }
            return Integer.compare(b.getCount(), a.getCount());
        });

        for (int i = 0; i < totalSlots; i++) {
            if (i < merged.size()) {
                session.setStack(i, merged.get(i).copy());
            } else {
                session.setStack(i, ItemStack.EMPTY);
            }
        }

        BlockState bState = client.level.getBlockState(session.getPos());
        if (bState.getBlock() instanceof EnderChestBlock) {
            Container clientEnder = player.getEnderChestInventory();
            for (int i = 0; i < totalSlots && i < clientEnder.getContainerSize(); i++) {
                clientEnder.setItem(i, session.getStack(i).copy());
            }
        }

        MinecraftServer server = client.getSingleplayerServer();
        if (server != null) {
            BlockPos targetPos = session.getPos();
            UUID playerUuid = player.getUUID();
            List<ItemStack> serverStacks = new ArrayList<>();
            for (int i = 0; i < totalSlots; i++) {
                serverStacks.add(session.getStack(i).copy());
            }

            server.execute(() -> {
                ServerPlayer sPlayer = server.getPlayerList().getPlayer(playerUuid);
                ServerLevel sWorld = server.getLevel(client.level.dimension());
                if (sPlayer == null || sWorld == null) return;

                Container sContainer = getContainerInventoryDirect(sWorld, targetPos, sPlayer);
                if (sContainer != null) {
                    for (int i = 0; i < sContainer.getContainerSize(); i++) {
                        if (i < serverStacks.size()) {
                            sContainer.setItem(i, serverStacks.get(i).copy());
                        } else {
                            sContainer.setItem(i, ItemStack.EMPTY);
                        }
                    }
                    sContainer.setChanged();
                    sPlayer.containerMenu.broadcastChanges();
                }
            });
        }

        session.triggerSortAnimation();
        HoloSoundHelper.playHoloSort();
    }

    public static int getMaterialTier(String regId) {
        if (regId.contains("netherite")) return 0;
        if (regId.contains("diamond")) return 1;
        if (regId.contains("iron")) return 2;
        if (regId.contains("golden") || regId.contains("gold")) return 3;
        if (regId.contains("chainmail")) return 4;
        if (regId.contains("stone")) return 5;
        if (regId.contains("wooden") || regId.contains("wood")) return 6;
        if (regId.contains("leather")) return 7;
        return 8;
    }

    public static int getArmorSlotIndex(String regId) {
        if (regId.contains("helmet") || regId.contains("cap")) return 0;
        if (regId.contains("chestplate") || regId.contains("tunic") || regId.contains("elytra")) return 1;
        if (regId.contains("leggings") || regId.contains("pants")) return 2;
        if (regId.contains("boots") || regId.contains("shoes")) return 3;
        return 4;
    }

    public static int getWeaponToolSubIndex(String regId) {
        if (regId.contains("sword")) return 0;
        if (regId.contains("axe") && !regId.contains("pickaxe")) return 1;
        if (regId.contains("pickaxe")) return 2;
        if (regId.contains("shovel")) return 3;
        if (regId.contains("hoe")) return 4;
        if (regId.contains("mace")) return 5;
        if (regId.contains("trident")) return 6;
        if (regId.contains("bow") && !regId.contains("crossbow")) return 7;
        if (regId.contains("crossbow")) return 8;
        if (regId.contains("shield")) return 9;
        if (regId.contains("shears")) return 10;
        if (regId.contains("fishing_rod")) return 11;
        if (regId.contains("flint_and_steel")) return 12;
        return 13;
    }

    public static int getMineralTier(String regId) {
        if (regId.contains("netherite")) return 0;
        if (regId.contains("diamond")) return 1;
        if (regId.contains("emerald")) return 2;
        if (regId.contains("gold")) return 3;
        if (regId.contains("iron")) return 4;
        if (regId.contains("copper")) return 5;
        if (regId.contains("lapis")) return 6;
        if (regId.contains("redstone")) return 7;
        if (regId.contains("coal")) return 8;
        if (regId.contains("quartz")) return 9;
        if (regId.contains("amethyst")) return 10;
        return 11;
    }

    public static int getItemSortCategory(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 999;
        Item item = stack.getItem();
        String regId = BuiltInRegistries.ITEM.getKey(item).getPath();

        if (regId.contains("sword") || (regId.contains("axe") && !regId.contains("pickaxe"))
                || regId.contains("pickaxe") || regId.contains("shovel") || regId.contains("hoe")
                || regId.contains("mace") || regId.contains("trident") || regId.contains("bow")
                || regId.contains("shield") || regId.contains("shears") || regId.contains("fishing_rod")
                || regId.contains("flint_and_steel") || stack.has(DataComponents.WEAPON)
                || (stack.has(DataComponents.TOOL) && !regId.contains("helmet") && !regId.contains("chestplate") && !regId.contains("leggings") && !regId.contains("boots"))) {
            return 0;
        }

        if (regId.contains("helmet") || regId.contains("chestplate") || regId.contains("leggings")
                || regId.contains("boots") || regId.contains("elytra") || regId.contains("cap")
                || regId.contains("tunic") || regId.contains("pants") || regId.contains("shoes")
                || stack.has(DataComponents.EQUIPPABLE)) {
            return 1;
        }

        if (regId.contains("netherite") || regId.contains("diamond") || regId.contains("emerald")
                || regId.contains("gold") || regId.contains("iron") || regId.contains("copper")
                || regId.contains("lapis") || regId.contains("redstone") || regId.contains("coal")
                || regId.contains("quartz") || regId.contains("amethyst") || regId.startsWith("raw_")
                || regId.endsWith("_ingot") || regId.endsWith("_nugget")) {
            return 2;
        }

        if (stack.has(DataComponents.FOOD)
                || regId.contains("potion") || regId.contains("golden_apple") || regId.contains("carrot")
                || regId.contains("bread") || regId.contains("cooked_") || regId.contains("beef")
                || regId.contains("porkchop") || regId.contains("chicken") || regId.contains("mutton")
                || regId.contains("apple")) {
            return 3;
        }

        if (regId.contains("piston") || regId.contains("observer") || regId.contains("repeater")
                || regId.contains("comparator") || regId.contains("dispenser") || regId.contains("dropper")
                || regId.contains("hopper") || regId.contains("lever") || regId.contains("button")
                || regId.contains("pressure_plate") || regId.contains("rail") || regId.contains("target")
                || regId.contains("daylight_detector") || regId.contains("tripwire_hook")) {
            return 4;
        }

        if (item instanceof BlockItem bi && (bi.getBlock() instanceof ShulkerBoxBlock || bi.getBlock() instanceof ChestBlock || bi.getBlock() instanceof BarrelBlock)) {
            return 5;
        }
        if (regId.contains("torch") || regId.contains("lantern") || regId.contains("bucket")
                || regId.contains("compass") || regId.contains("clock") || regId.contains("bundle")
                || regId.contains("lead") || regId.contains("name_tag") || regId.contains("saddle")) {
            return 5;
        }

        if (item instanceof BlockItem) {
            return 6;
        }

        if (regId.contains("bone") || regId.contains("string") || regId.contains("feather")
                || regId.contains("leather") || regId.contains("gunpowder") || regId.contains("ender_pearl")
                || regId.contains("blaze_rod") || regId.contains("slime_ball") || regId.contains("spider_eye")
                || regId.contains("rotten_flesh") || regId.contains("seed") || regId.contains("wheat")
                || regId.contains("egg")) {
            return 7;
        }

        return 8;
    }

    public void tick() {
        flyingItems.removeIf(FlyingItem::isFinished);
        if (activeSessions.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            forceCloseAll();
            return;
        }

        if (client.level.getGameTime() % 4 == 0) {
            for (HoloSession session : activeSessions.values()) {
                if (!session.isClosing() && (session.isFurnace() || session.getCachedSize() <= 9)) {
                    syncSessionFromServer(session);
                }
            }
        }

        for (HoloSession session : activeSessions.values()) {
            BlockPos pos = session.getPos();
            double distSq = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distSq > 40.0 * 40.0) {
                startClosingSession(session);
            }
        }

        activeSessions.values().removeIf(session -> {
            if (session.isFullyClosed()) {
                closeBarrelBlock(session.getPos());
                if (session.getPartnerPos() != null) {
                    closeBarrelBlock(session.getPartnerPos());
                }
                return true;
            }
            return false;
        });
    }
}
