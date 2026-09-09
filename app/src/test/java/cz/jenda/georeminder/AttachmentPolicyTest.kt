package cz.jenda.georeminder

import cz.jenda.georeminder.data.AttachmentHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentPolicyTest {

    @Test
    fun `povoli jen jpeg png a pdf`() {
        assertTrue(AttachmentHelper.isAllowedAttachmentMimeType("image/jpeg"))
        assertTrue(AttachmentHelper.isAllowedAttachmentMimeType("IMAGE/PNG"))
        assertTrue(AttachmentHelper.isAllowedAttachmentMimeType("application/pdf"))
    }

    @Test
    fun `odmitne ostatni nebo chybejici mime typy`() {
        assertFalse(AttachmentHelper.isAllowedAttachmentMimeType("image/webp"))
        assertFalse(AttachmentHelper.isAllowedAttachmentMimeType("text/plain"))
        assertFalse(AttachmentHelper.isAllowedAttachmentMimeType("application/octet-stream"))
        assertFalse(AttachmentHelper.isAllowedAttachmentMimeType(null))
    }
}
