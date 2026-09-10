const uploadArea = document.getElementById('uploadArea');
        const fileInput = document.getElementById('fileInput');
        const previewImg = document.getElementById('previewImg');

        function resetPublishCoverSelection() {
            window.__pubCoverFile = null;
            if (fileInput) fileInput.value = '';
            if (previewImg) previewImg.src = '';
            if (uploadArea) uploadArea.classList.remove('has-image');
        }

        window.resetPublishCoverSelection = resetPublishCoverSelection;
        resetPublishCoverSelection();
        window.addEventListener('pageshow', () => {
            resetPublishCoverSelection();
        });

        uploadArea.addEventListener('click', () => {
            fileInput.click();
        });

        fileInput.addEventListener('change', (e) => {
            const file = e.target.files[0];
            if (file) {
                window.__pubCoverFile = file;
                const reader = new FileReader();
                reader.onload = (e) => {
                    previewImg.src = e.target.result;
                    uploadArea.classList.add('has-image');
                }
                reader.readAsDataURL(file);
            } else {
                resetPublishCoverSelection();
            }
        });

        // Simple Drag and Drop
        uploadArea.addEventListener('dragover', (e) => {
            e.preventDefault();
            uploadArea.style.borderColor = 'var(--accent-color)';
            uploadArea.style.backgroundColor = '#f0f7ff';
        });

        uploadArea.addEventListener('dragleave', (e) => {
            e.preventDefault();
            uploadArea.style.borderColor = '#e0e0e0';
            uploadArea.style.backgroundColor = '#fafafa';
        });

        uploadArea.addEventListener('drop', (e) => {
            e.preventDefault();
            const file = e.dataTransfer.files[0];
            if (file && file.type.startsWith('image/')) {
                window.__pubCoverFile = file;
                const reader = new FileReader();
                reader.onload = (e) => {
                    previewImg.src = e.target.result;
                    uploadArea.classList.add('has-image');
                }
                reader.readAsDataURL(file);
            } else {
                resetPublishCoverSelection();
            }
        });