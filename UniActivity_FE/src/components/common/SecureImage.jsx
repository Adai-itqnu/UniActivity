import { useEffect, useState } from 'react'

export default function SecureImage({ src, alt, ...props }) {
    const [result, setResult] = useState({ src: null, objectUrl: null, failed: false })

    useEffect(() => {
        let active = true
        let createdUrl = null
        fetch(src)
            .then(response => {
                if (!response.ok) throw new Error('Không thể tải ảnh minh chứng')
                return response.blob()
            })
            .then(blob => {
                if (!active) return
                createdUrl = URL.createObjectURL(blob)
                setResult({ src, objectUrl: createdUrl, failed: false })
            })
            .catch(() => {
                if (active) setResult({ src, objectUrl: null, failed: true })
            })

        return () => {
            active = false
            if (createdUrl) URL.revokeObjectURL(createdUrl)
        }
    }, [src])

    if (result.src === src && result.failed) {
        return <div role="img" aria-label={alt} {...props}>Không thể tải ảnh</div>
    }
    if (result.src !== src || !result.objectUrl) {
        return <div role="status" {...props}>Đang tải ảnh…</div>
    }
    return <img src={result.objectUrl} alt={alt} {...props} />
}
