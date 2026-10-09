package zhiqiu.app.cs.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 应用用到的图标。
 *
 * 直接内置 Material 的 24dp 路径而不是引 material-icons 库：那个库在 Compose Multiplatform
 * 1.8 之后不再发布 wasmJs 产物，而这里只需要十几个图标，内置反而少一个（很大的）依赖。
 */
internal object AppIcons {
    val Clipboard: ImageVector = icon("clipboard", ContentPastePath)
    val Copy: ImageVector = icon("copy", ContentCopyPath)
    val Pin: ImageVector = icon("pin", PushPinPath)
    val Delete: ImageVector = icon("delete", DeletePath)
    val Download: ImageVector = icon("download", FileDownloadPath)
    val OpenInNew: ImageVector = icon("openInNew", OpenInNewPath)
    val Send: ImageVector = icon("send", SendPath)
    val Attach: ImageVector = icon("attach", LinkPath)
    val Search: ImageVector = icon("search", SearchPath)
    val Close: ImageVector = icon("close", ClosePath)
    val Shield: ImageVector = icon("shield", VerifiedUserPath)
    val Refresh: ImageVector = icon("refresh", RefreshPath)
    val Check: ImageVector = icon("check", CheckCirclePath)
    val Warning: ImageVector = icon("warning", WarningPath)
    val Image: ImageVector = icon("image", ImageOutlinePath)
    val Description: ImageVector = icon("description", DescriptionPath)
    val Key: ImageVector = icon("key", VpnKeyPath)
    val Lock: ImageVector = icon("lock", LockPath)
    val Devices: ImageVector = icon("devices", LaptopPath)
    val Logout: ImageVector = icon("logout", LogoutPath)
    val ChevronRight: ImageVector = icon("chevronRight", ChevronRightPath)
    val Add: ImageVector = icon("add", AddPath)
    val Undo: ImageVector = icon("undo", UndoPath)
    val Edit: ImageVector = icon("edit", EditPath)
    val Info: ImageVector = icon("info", InfoPath)
    val Note: ImageVector = icon("note", EventNotePath)
    val Up: ImageVector = icon("up", ArrowUpwardPath)
    val Down: ImageVector = icon("down", ArrowDownwardPath)
    val SelectAll: ImageVector = icon("selectAll", DoneAllPath)
    val Duplicate: ImageVector = icon("duplicate", FilterNonePath)
    val Transform: ImageVector = icon("transform", TextFormatPath)
    val More: ImageVector = icon("more", MoreVertPath)
    val Save: ImageVector = icon("save", SavePath)
    val Export: ImageVector = icon("export", FileUploadPath)
    val Eye: ImageVector = icon("eye", RemoveRedEyePath)
}

private const val EditPath =
        "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z"

private const val InfoPath =
        "M11,7h2v2h-2zM11,11h2v6h-2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,17v-4h2v4h-2zM14,17h-2v-6h-2v-2h4v8z"

private const val EventNotePath =
        "M17,10L7,10v2h10v-2zM19,3h-1L18,1h-2v2L8,3L8,1L6,1v2L5,3c-1.11,0 -1.99,0.9 -1.99,2L3,19c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2L21,5c0,-1.1 -0.9,-2 -2,-2zM19,19L5,19L5,8h14v11zM14,14L7,14v2h7v-2z"

private const val ArrowUpwardPath = "M4,12l1.41,1.41L11,7.83V20h2V7.83l5.58,5.59L20,12l-8,-8 -8,8z"

private const val ArrowDownwardPath = "M20,12l-1.41,-1.41L13,16.17V4h-2v12.17l-5.58,-5.59L4,12l8,8 8,-8z"

private const val DoneAllPath =
        "M18,7l-1.41,-1.41 -6.34,6.34 1.41,1.41L18,7zM22.24,5.59L11.66,16.17 7.48,12l-1.41,1.41L11.66,19l12,-12 -1.42,-1.41zM0.41,13.41L4,17l1.41,-1.41L1.83,12 0.41,13.41z"

private const val FilterNonePath =
        "M3,5L1,5v16c0,1.1 0.9,2 2,2h16v-2L3,21L3,5zM21,1L7,1c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2L23,3c0,-1.1 -0.9,-2 -2,-2zM21,17L7,17L7,3h14v14z"

private const val TextFormatPath =
        "M5,17v2h14v-2L5,17zM9.5,12.8h5l0.9,2.2h2.1L12.7,4h-1.4L6.5,15h2.1l0.9,-2.2zM12,5.98L13.87,11h-3.74L12,5.98z"

private const val MoreVertPath =
        "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z"

private const val SavePath =
        "M17,3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V7l-4,-4zM12,19c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3 3,1.34 3,3 -1.34,3 -3,3zM15,9H5V5h10v4z"

private const val FileUploadPath = "M9,16h6v-6h4l-7,-7 -7,7h4v6zM5,18h14v2H5v-2z"

private const val RemoveRedEyePath =
        "M12,6c3.79,0 7.17,2.13 8.82,5.5C19.17,14.87 15.79,17 12,17s-7.17,-2.13 -8.82,-5.5C4.83,8.13 8.21,6 12,6zM12,4C7,4 2.73,7.11 1,11.5 2.73,15.89 7,19 12,19s9.27,-3.11 11,-7.5C21.27,7.11 17,4 12,4zM12,9c1.38,0 2.5,1.12 2.5,2.5S13.38,14 12,14s-2.5,-1.12 -2.5,-2.5S10.62,9 12,9zM12,7c-2.48,0 -4.5,2.02 -4.5,4.5S9.52,16 12,16s4.5,-2.02 4.5,-4.5S14.48,7 12,7z"

private const val UndoPath =
        "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,9v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.95,0 7.29,2.63 8.33,6.26L23.09,14.94C21.88,10.96 17.65,8 12.5,8z"

private const val AddPath = "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"

private const val ContentPastePath =
    "M19,2h-4.18C14.4,0.84 13.3,0 12,0c-1.3,0 -2.4,0.84 -2.82,2H5c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM12,2c0.55,0 1,0.45 1,1s-0.45,1 -1,1 -1,-0.45 -1,-1 0.45,-1 1,-1zM19,20H5V4h2v3h10V4h2v16z"

private const val ContentCopyPath =
    "M16,1H4C2.9,1 2,1.9 2,3v14h2V3h12V1zM19,5H8C6.9,5 6,5.9 6,7v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7C21,5.9 20.1,5 19,5zM19,21H8V7h11V21z"

private const val PushPinPath =
    "M16,9V4h1c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1H7C6.45,2 6,2.45 6,3s0.45,1 1,1h1v5c0,1.66 -1.34,3 -3,3v2h5.97v7l1,1 1,-1v-7H19v-2c-1.66,0 -3,-1.34 -3,-3z"

private const val DeletePath =
    "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6V19zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z"

private const val FileDownloadPath = "M19,9h-4V3H9v6H5l7,7L19,9zM5,18v2h14v-2H5z"

private const val OpenInNewPath =
    "M19,19H5V5h7V3H5C3.89,3 3,3.9 3,5v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2V19zM14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3H14z"

private const val SendPath = "M2.01,21L23,12 2.01,3 2,10l15,2 -15,2z"

private const val LinkPath =
    "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7C4.24,7 2,9.24 2,12s2.24,5 5,5h4v-1.9H7c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8.5,11h7v2h-7V11zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z"

private const val SearchPath =
    "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z"

private const val ClosePath =
    "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z"

private const val VerifiedUserPath =
    "M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12V5L12,1zM10.94,15.54L7.5,12.1l1.41,-1.41 2.03,2.03 4.65,-4.65 1.41,1.41 -6.06,6.06z"

private const val RefreshPath =
    "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -8,8s3.58,8 8,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z"

private const val CheckCirclePath =
    "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z"

private const val WarningPath = "M1,21h22L12,2 1,21zM13,18h-2v-2h2v2zM13,14h-2v-4h2v4z"

private const val ImageOutlinePath =
    "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM8.5,8.5c0,0.83 -0.67,1.5 -1.5,1.5S5.5,9.33 5.5,8.5 6.17,7 7,7s1.5,0.67 1.5,1.5zM19,17l-4.5,-6 -3.5,4.5 -2.5,-3L5,17h14z"

private const val DescriptionPath =
    "M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6zM16,18H8v-2h8v2zM16,14H8v-2h8v2zM13,9V3.5L18.5,9H13z"

private const val VpnKeyPath =
    "M12.65,10C11.83,7.67 9.61,6 7,6c-3.31,0 -6,2.69 -6,6s2.69,6 6,6c2.61,0 4.83,-1.67 5.65,-4H17v4h4v-4h2v-2H12.65zM7,14c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2z"

private const val LockPath =
    "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1s3.1,1.39 3.1,3.1v2z"

private const val LaptopPath =
    "M20,18c1.1,0 1.99,-0.9 1.99,-2L22,5c0,-1.1 -0.9,-2 -2,-2H4c-1.1,0 -2,0.9 -2,2v11c0,1.1 0.9,2 2,2H0v2h24v-2h-4zM4,5h16v11H4V5z"

private const val LogoutPath =
    "M17,7l-1.41,1.41L18.17,11H8v2h10.17l-2.58,2.58L17,17l5,-5zM4,5h8V3H4c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h8v-2H4V5z"

private const val ChevronRightPath = "M8.59,16.34l4.58,-4.59 -4.58,-4.59L10,5.75l6,6 -6,6z"

private fun icon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
        .build()
